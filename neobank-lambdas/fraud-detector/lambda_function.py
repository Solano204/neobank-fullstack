import json
import boto3
import os
from datetime import datetime

dynamodb = boto3.resource('dynamodb')
sns = boto3.client('sns')

TABLE_NAME = os.environ['DYNAMODB_TABLE']
SNS_TOPIC = os.environ['SNS_FRAUD_TOPIC']

def lambda_handler(event, context):
    for record in event['Records']:
        try:
            transaction = json.loads(record['body'])
            fraud_score = calculate_fraud_score(transaction)
            update_fraud_score(transaction['transaction_id'], fraud_score)

            if fraud_score > 0.75:
                send_fraud_alert(transaction, fraud_score)
                freeze_transaction(transaction)

            print(f"Fraud check: {transaction['transaction_id']} - Score: {fraud_score}")
        except Exception as e:
            print(f"Error: {str(e)}")

    return {'statusCode': 200}

def calculate_fraud_score(transaction):
    score = 0.0
    amount = float(transaction['amount'])

    if amount > 5000:
        score += 0.3

    hour = datetime.now().hour
    if hour < 6 or hour > 23:
        score += 0.2

    score += 0.15
    return min(score, 1.0)

def update_fraud_score(transaction_id, fraud_score):
    table = dynamodb.Table(TABLE_NAME)
    table.update_item(
        Key={'transaction_id': transaction_id},
        UpdateExpression='SET fraud_score = :score, metadata.fraud_checked_at = :time',
        ExpressionAttributeValues={
            ':score': fraud_score,
            ':time': datetime.now().isoformat()
        }
    )

def send_fraud_alert(transaction, fraud_score):
    message = f"""
    FRAUD ALERT
    Transaction ID: {transaction['transaction_id']}
    Amount: ${transaction['amount']}
    From: {transaction['from_account']}
    To: {transaction['to_account']}
    Risk Score: {fraud_score:.2f}
    Time: {datetime.now().isoformat()}
    """
    sns.publish(TopicArn=SNS_TOPIC, Subject='FRAUD ALERT', Message=message)

def freeze_transaction(transaction):
    table = dynamodb.Table(TABLE_NAME)
    table.update_item(
        Key={'transaction_id': transaction['transaction_id']},
        UpdateExpression='SET #status = :frozen',
        ExpressionAttributeNames={'#status': 'status'},
        ExpressionAttributeValues={':frozen': 'FROZEN_FRAUD'}
    )
