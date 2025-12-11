# PowerShell deployment script for Event Site Manager Batch Jobs Service
# This script builds, tags, and pushes the Docker image to AWS ECR

param(
    [string]$AwsRegion = "us-east-1",
    [string]$EcrRepository = "event-site-manager-batch-jobs",
    [string]$AwsAccountId = ""
)

$ErrorActionPreference = "Stop"

Write-Host "Starting deployment process..." -ForegroundColor Green

# Check if AWS CLI is installed
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    Write-Host "AWS CLI is not installed. Please install it first." -ForegroundColor Red
    exit 1
}

# Check if Docker is installed
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker is not installed. Please install it first." -ForegroundColor Red
    exit 1
}

# Get AWS account ID if not provided
if ([string]::IsNullOrEmpty($AwsAccountId)) {
    Write-Host "Getting AWS account ID..." -ForegroundColor Yellow
    $AwsAccountId = (aws sts get-caller-identity --query Account --output text).Trim()
}

$EcrUri = "${AwsAccountId}.dkr.ecr.${AwsRegion}.amazonaws.com/${EcrRepository}"

Write-Host "ECR Repository: $EcrUri" -ForegroundColor Green

# Build the application
Write-Host "Building application with Maven..." -ForegroundColor Yellow
mvn clean package -DskipTests

# Login to ECR
Write-Host "Logging in to ECR..." -ForegroundColor Yellow
$ecrPassword = aws ecr get-login-password --region $AwsRegion
$ecrPassword | docker login --username AWS --password-stdin $EcrUri

# Check if repository exists, create if not
$repoExists = aws ecr describe-repositories --repository-names $EcrRepository --region $AwsRegion 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating ECR repository..." -ForegroundColor Yellow
    aws ecr create-repository --repository-name $EcrRepository --region $AwsRegion
}

# Build Docker image
Write-Host "Building Docker image..." -ForegroundColor Yellow
docker build -t "${EcrRepository}:latest" .

# Tag image
Write-Host "Tagging Docker image..." -ForegroundColor Yellow
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
docker tag "${EcrRepository}:latest" "${EcrUri}:latest"
docker tag "${EcrRepository}:latest" "${EcrUri}:${timestamp}"

# Push image
Write-Host "Pushing Docker image to ECR..." -ForegroundColor Yellow
docker push "${EcrUri}:latest"
docker push "${EcrUri}:${timestamp}"

Write-Host "Deployment completed successfully!" -ForegroundColor Green
Write-Host "Image URI: ${EcrUri}:latest" -ForegroundColor Green

# Update ECS task definition
Write-Host "To update ECS task definition, run:" -ForegroundColor Yellow
Write-Host "aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json"




