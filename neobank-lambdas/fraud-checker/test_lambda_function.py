import json
import os
from datetime import datetime
from decimal import Decimal
from unittest.mock import MagicMock

import pytest

os.environ.setdefault("DYNAMODB_TABLE", "transactions")
os.environ.setdefault("SNS_FRAUD_TOPIC", "arn:aws:sns:us-east-1:123456789012:fraud-alerts")

import lambda_function


class _FixedDateTime(datetime):
    """Base class for monkeypatching lambda_function.datetime.now() deterministically."""

    _fixed = datetime(2024, 6, 10, 14, 30, 0)  # Monday afternoon

    @classmethod
    def now(cls, tz=None):
        return cls._fixed


def _fixed_datetime(moment):
    class Fixed(_FixedDateTime):
        _fixed = moment

    return Fixed


WEEKDAY_AFTERNOON = datetime(2024, 6, 10, 14, 30, 0)  # Monday
WEEKEND_AFTERNOON = datetime(2024, 6, 8, 14, 30, 0)  # Saturday
WEEKDAY_NIGHT = datetime(2024, 6, 10, 3, 0, 0)  # Monday, 3 AM


def test_small_ordinary_amount_scores_zero(monkeypatch):
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKDAY_AFTERNOON))
    score = lambda_function.calculate_fraud_score({"amount": 100}, MagicMock())
    assert score == pytest.approx(0.0)


def test_high_non_round_amount_only_adds_the_amount_risk(monkeypatch):
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKDAY_AFTERNOON))
    score = lambda_function.calculate_fraud_score({"amount": 5500}, MagicMock())
    assert score == pytest.approx(0.3)


def test_round_number_amount_adds_its_own_risk(monkeypatch):
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKDAY_AFTERNOON))
    score = lambda_function.calculate_fraud_score({"amount": 1000}, MagicMock())
    assert score == pytest.approx(0.15)


def test_weekend_transaction_adds_risk(monkeypatch):
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKEND_AFTERNOON))
    score = lambda_function.calculate_fraud_score({"amount": 100}, MagicMock())
    assert score == pytest.approx(0.1)


def test_unusual_hour_adds_risk(monkeypatch):
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKDAY_NIGHT))
    score = lambda_function.calculate_fraud_score({"amount": 100}, MagicMock())
    assert score == pytest.approx(0.2)


def test_worst_case_score_hits_exactly_the_freeze_threshold(monkeypatch):
    """
    Max achievable score with the current weights (0.3 high-amount +
    0.2 odd-hour + 0.1 weekend + 0.15 round-number) is exactly 0.75.
    lambda_handler's freeze check is ">= 0.75" specifically so this case
    still triggers the freeze/alert path instead of silently passing.
    """
    worst_case = datetime(2024, 6, 8, 3, 0, 0)  # Saturday, 3 AM
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(worst_case))

    score = lambda_function.calculate_fraud_score({"amount": 6000}, MagicMock())

    assert score == pytest.approx(0.75)


def test_update_fraud_score_writes_score_and_timestamp(monkeypatch):
    mock_table = MagicMock()
    mock_dynamodb = MagicMock()
    mock_dynamodb.Table.return_value = mock_table
    monkeypatch.setattr(lambda_function, "dynamodb", mock_dynamodb)
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKDAY_AFTERNOON))

    lambda_function.update_fraud_score("txn_1", 0.42, MagicMock())

    mock_dynamodb.Table.assert_called_once_with(lambda_function.TABLE_NAME)
    _, kwargs = mock_table.update_item.call_args
    assert kwargs["Key"] == {"transaction_id": "txn_1"}
    assert kwargs["ExpressionAttributeValues"][":score"] == Decimal("0.42")


def test_send_fraud_alert_publishes_to_the_configured_topic(monkeypatch):
    mock_sns = MagicMock()
    mock_sns.publish.return_value = {"MessageId": "msg-1"}
    monkeypatch.setattr(lambda_function, "sns", mock_sns)

    transaction = {
        "transaction_id": "txn_1",
        "amount": 6000,
        "from_account": "111111111111111111",
        "to_account": "222222222222222222",
    }
    lambda_function.send_fraud_alert(transaction, 0.9, MagicMock())

    _, kwargs = mock_sns.publish.call_args
    assert kwargs["TopicArn"] == lambda_function.SNS_TOPIC
    assert "txn_1" in kwargs["Message"]


def test_freeze_transaction_marks_the_record_frozen(monkeypatch):
    mock_table = MagicMock()
    mock_dynamodb = MagicMock()
    mock_dynamodb.Table.return_value = mock_table
    monkeypatch.setattr(lambda_function, "dynamodb", mock_dynamodb)

    lambda_function.freeze_transaction("txn_1", MagicMock())

    _, kwargs = mock_table.update_item.call_args
    assert kwargs["Key"] == {"transaction_id": "txn_1"}
    assert kwargs["ExpressionAttributeValues"][":frozen"] == "FROZEN_FRAUD"


def test_lambda_handler_reports_only_the_failed_message_for_retry(monkeypatch):
    mock_table = MagicMock()
    mock_dynamodb = MagicMock()
    mock_dynamodb.Table.return_value = mock_table
    mock_sns = MagicMock()
    monkeypatch.setattr(lambda_function, "dynamodb", mock_dynamodb)
    monkeypatch.setattr(lambda_function, "sns", mock_sns)
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(WEEKDAY_AFTERNOON))

    event = {
        "Records": [
            {"messageId": "msg-1", "body": json.dumps({
                "transaction_id": "txn_1",
                "amount": 100,
                "from_account": "111111111111111111",
                "to_account": "222222222222222222",
            })},
            {"messageId": "msg-2", "body": "not-valid-json"},
        ]
    }

    response = lambda_function.lambda_handler(event, MagicMock())

    # Only the message that actually failed is reported back to SQS for
    # redelivery -- the one that succeeded must not be reprocessed.
    assert response == {"batchItemFailures": [{"itemIdentifier": "msg-2"}]}


def test_lambda_handler_freezes_and_alerts_on_worst_case_score(monkeypatch):
    mock_table = MagicMock()
    mock_dynamodb = MagicMock()
    mock_dynamodb.Table.return_value = mock_table
    mock_sns = MagicMock()
    mock_sns.publish.return_value = {"MessageId": "msg-1"}
    monkeypatch.setattr(lambda_function, "dynamodb", mock_dynamodb)
    monkeypatch.setattr(lambda_function, "sns", mock_sns)
    worst_case = datetime(2024, 6, 8, 3, 0, 0)  # Saturday, 3 AM
    monkeypatch.setattr(lambda_function, "datetime", _fixed_datetime(worst_case))

    event = {
        "Records": [
            {"messageId": "msg-1", "body": json.dumps({
                "transaction_id": "txn_1",
                "amount": 6000,
                "from_account": "111111111111111111",
                "to_account": "222222222222222222",
            })},
        ]
    }

    response = lambda_function.lambda_handler(event, MagicMock())

    assert response == {"batchItemFailures": []}
    mock_sns.publish.assert_called_once()
    update_calls = mock_table.update_item.call_args_list
    assert any(c.kwargs["ExpressionAttributeValues"].get(":frozen") == "FROZEN_FRAUD" for c in update_calls)
