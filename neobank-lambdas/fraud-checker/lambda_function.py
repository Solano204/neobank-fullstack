import json
import boto3
import os
from datetime import datetime
from decimal import Decimal

dynamodb = boto3.resource('dynamodb')
sns = boto3.client('sns')

TABLE_NAME = os.environ['DYNAMODB_TABLE']
SNS_TOPIC = os.environ['SNS_FRAUD_TOPIC']

def lambda_handler(event, context):
    success_count = 0
    failure_count = 0

    for record in event['Records']:
        try:
            transaction = json.loads(record['body'])

            fraud_score = calculate_fraud_score(transaction, context)

            update_fraud_score(transaction['transaction_id'], fraud_score, context)

            if fraud_score > 0.75:
                send_fraud_alert(transaction, fraud_score, context)
                freeze_transaction(transaction['transaction_id'], context)

            success_count += 1
            context.log(f"Fraud check complete: {transaction['transaction_id']} - Score: {fraud_score:.2f}")

        except Exception as e:
            failure_count += 1
            context.log(f"Error processing fraud check: {str(e)}")
            print(f"Error details: {str(e)}")

    context.log(f"Fraud checks complete. Success: {success_count}, Failures: {failure_count}")

    return {
        'statusCode': 200,
        'body': json.dumps({'success': success_count, 'failures': failure_count})
    }

def calculate_fraud_score(transaction, context):
    score = 0.0
    amount = float(transaction.get('amount', 0))

    # High amount check
    if amount > 5000:
        score += 0.3
        context.log(f"High amount detected: {amount}")

    # Time of day check
    hour = datetime.now().hour
    if hour < 6 or hour > 23:
        score += 0.2
        context.log(f"Unusual time detected: {hour}:00")

    # Weekend check
    if datetime.now().weekday() >= 5:
        score += 0.1
        context.log("Weekend transaction detected")

    # Round numbers check (potential automated attack)
    if amount % 1000 == 0:
        score += 0.15
        context.log("Round number transaction detected")

    return min(score, 1.0)

def update_fraud_score(transaction_id, fraud_score, context):
    try:
        table = dynamodb.Table(TABLE_NAME)

        response = table.update_item(
            Key={'transaction_id': transaction_id},
            UpdateExpression='SET fraud_score = :score, fraud_checked_at = :time',
            ExpressionAttributeValues={
                ':score': Decimal(str(fraud_score)),
                ':time': datetime.now().isoformat()
            },
            ReturnValues='UPDATED_NEW'
        )

        context.log(f"Updated fraud score for {transaction_id}: {fraud_score:.2f}")

    except Exception as e:
        context.log(f"Error updating fraud score: {str(e)}")
        raise

def send_fraud_alert(transaction, fraud_score, context):
    try:
        message = f"""
FRAUD ALERT

Transaction ID: {transaction['transaction_id']}
Amount: ${transaction['amount']:.2f} MXN
From Account: {transaction['from_account']}
To Account: {transaction['to_account']}
Risk Score: {fraud_score:.2f}
Timestamp: {datetime.now().isoformat()}

Reason: High fraud risk detected
Action: Transaction frozen for review

Please investigate immediately.
"""

        response = sns.publish(
            TopicArn=SNS_TOPIC,
            Subject='HIGH RISK TRANSACTION DETECTED',
            Message=message
        )

        context.log(f"Fraud alert sent. MessageId: {response['MessageId']}")

    except Exception as e:
        context.log(f"Error sending fraud alert: {str(e)}")

def freeze_transaction(transaction_id, context):
    try:
        table = dynamodb.Table(TABLE_NAME)

        table.update_item(
            Key={'transaction_id': transaction_id},
            UpdateExpression='SET #status = :frozen, frozen_at = :time, frozen_reason = :reason',
            ExpressionAttributeNames={
                '#status': 'status'
            },
            ExpressionAttributeValues={
                ':frozen': 'FROZEN_FRAUD',
                ':time': datetime.now().isoformat(),
                ':reason': 'High fraud risk score'
            }
        )

        context.log(f"Transaction {transaction_id} frozen")

    except Exception as e:
        context.log(f"Error freezing transaction: {str(e)}")
        raise