#!/bin/bash
set -e

# Install Java 21
dnf install java-21-amazon-corretto -y

# Create log directory
mkdir -p /var/log/neobank
chown ec2-user:ec2-user /var/log/neobank

# Write .env file
cat > /home/ec2-user/.env << 'ENVEOF'
SPRING_PROFILES_ACTIVE=prod
DB_HOST=${db_host}
DB_PORT=5432
DB_NAME=neobank_db
DB_USERNAME=postgres
DB_PASSWORD=${db_password}
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=${aws_access_key_id}
AWS_SECRET_ACCESS_KEY=${aws_secret_access_key}
AWS_COGNITO_USER_POOL_ID=${cognito_user_pool_id}
AWS_COGNITO_CLIENT_ID=${cognito_client_id}
AWS_S3_BUCKET_NAME=${s3_bucket_name}
AWS_SQS_QUEUE_URL=${sqs_queue_url}
AWS_SES_FROM_EMAIL=${ses_from_email}
JWT_SECRET=${jwt_secret}
JWT_EXPIRATION=3600000
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=${cors_allowed_origins}
ENVEOF

chown ec2-user:ec2-user /home/ec2-user/.env
chmod 600 /home/ec2-user/.env

# Create systemd service
cat > /etc/systemd/system/neobank.service << 'SVCEOF'
[Unit]
Description=NeoBank Spring Boot Application
After=network.target

[Service]
User=ec2-user
EnvironmentFile=/home/ec2-user/.env
ExecStart=/usr/bin/java -Xmx400m -Xms200m -jar /home/ec2-user/neobank-backend-1.0.0.jar
SuccessExitStatus=143
Restart=always
RestartSec=10
StandardOutput=append:/var/log/neobank/neobank-backend.log
StandardError=append:/var/log/neobank/neobank-backend.log

[Install]
WantedBy=multi-user.target
SVCEOF

systemctl daemon-reload
systemctl enable neobank
echo "Bootstrap complete — upload JAR then: sudo systemctl start neobank"