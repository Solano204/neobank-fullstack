#!/bin/bash

set -e

echo "========================================="
echo "DEPLOYING ALL LAMBDA FUNCTIONS"
echo "========================================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Java Lambdas
JAVA_LAMBDAS=("transaction-service" "transaction-query" "kyc-validator" "ledger-writer" "notification-service" "analytics-processor")

for lambda in "${JAVA_LAMBDAS[@]}"; do
    echo -e "${YELLOW}Building $lambda...${NC}"
    cd $lambda
    mvn clean package

    echo -e "${GREEN}Deploying $lambda...${NC}"
    aws lambda update-function-code \
        --function-name $lambda \
        --zip-file fileb://target/$lambda-1.0.0.jar \
        --region us-east-1

    cd ..
    echo -e "${GREEN}✓ $lambda deployed${NC}"
    echo ""
done

# Python Lambda
echo -e "${YELLOW}Building fraud-checker...${NC}"
cd fraud-checker
pip install -r requirements.txt -t .
zip -r fraud-checker.zip .

echo -e "${GREEN}Deploying fraud-checker...${NC}"
aws lambda update-function-code \
    --function-name fraud-checker \
    --zip-file fileb://fraud-checker.zip \
    --region us-east-1

cd ..
echo -e "${GREEN}✓ fraud-checker deployed${NC}"
echo ""

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}ALL LAMBDA FUNCTIONS DEPLOYED SUCCESSFULLY${NC}"
echo -e "${GREEN}=========================================${NC}"