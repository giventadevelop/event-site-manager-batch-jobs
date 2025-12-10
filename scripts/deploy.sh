#!/bin/bash

# Deployment script for Event Site Manager Batch Jobs Service
# This script builds, tags, and pushes the Docker image to AWS ECR

set -e

# Configuration
AWS_REGION=${AWS_REGION:-us-east-1}
ECR_REPOSITORY=${ECR_REPOSITORY:-event-site-manager-batch-jobs}
AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID:-}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting deployment process...${NC}"

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}AWS CLI is not installed. Please install it first.${NC}"
    exit 1
fi

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed. Please install it first.${NC}"
    exit 1
fi

# Get AWS account ID if not provided
if [ -z "$AWS_ACCOUNT_ID" ]; then
    echo -e "${YELLOW}Getting AWS account ID...${NC}"
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
fi

ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}"

echo -e "${GREEN}ECR Repository: ${ECR_URI}${NC}"

# Build the application
echo -e "${YELLOW}Building application with Maven...${NC}"
mvn clean package -DskipTests

# Login to ECR
echo -e "${YELLOW}Logging in to ECR...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_URI}

# Check if repository exists, create if not
if ! aws ecr describe-repositories --repository-names ${ECR_REPOSITORY} --region ${AWS_REGION} &> /dev/null; then
    echo -e "${YELLOW}Creating ECR repository...${NC}"
    aws ecr create-repository --repository-name ${ECR_REPOSITORY} --region ${AWS_REGION}
fi

# Build Docker image
echo -e "${YELLOW}Building Docker image...${NC}"
docker build -t ${ECR_REPOSITORY}:latest .

# Tag image
echo -e "${YELLOW}Tagging Docker image...${NC}"
docker tag ${ECR_REPOSITORY}:latest ${ECR_URI}:latest
docker tag ${ECR_REPOSITORY}:latest ${ECR_URI}:$(date +%Y%m%d-%H%M%S)

# Push image
echo -e "${YELLOW}Pushing Docker image to ECR...${NC}"
docker push ${ECR_URI}:latest
docker push ${ECR_URI}:$(date +%Y%m%d-%H%M%S)

echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}Image URI: ${ECR_URI}:latest${NC}"

# Update ECS task definition
echo -e "${YELLOW}To update ECS task definition, run:${NC}"
echo "aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json"


