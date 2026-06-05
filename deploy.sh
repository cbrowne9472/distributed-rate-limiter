#!/usr/bin/env bash
# Usage: ./deploy.sh [image-tag]
# Builds the Docker image, pushes to ECR, and runs terraform apply.
# Requires: docker, aws CLI, terraform, and terraform/terraform.tfvars with db_password.
set -euo pipefail

PROJECT="rate-limiter"
REGION="${AWS_REGION:-us-east-1}"
IMAGE_TAG="${1:-$(git rev-parse --short HEAD)}"

# ── Preflight checks ──────────────────────────────────────────────────────────
if [ ! -f "terraform/terraform.tfvars" ]; then
  echo "ERROR: terraform/terraform.tfvars not found"
  echo "  cp terraform/terraform.tfvars.example terraform/terraform.tfvars"
  echo "  # then set db_password"
  exit 1
fi

echo "==> Getting AWS account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}"

echo "Account  : ${ACCOUNT_ID}"
echo "Region   : ${REGION}"
echo "ECR repo : ${ECR_REPO}"
echo "Tag      : ${IMAGE_TAG}"
echo ""

# ── Build ─────────────────────────────────────────────────────────────────────
echo "==> Building Docker image..."
docker build -t "${PROJECT}:${IMAGE_TAG}" .
echo "    Built ${PROJECT}:${IMAGE_TAG}"

# ── Push to ECR ───────────────────────────────────────────────────────────────
echo ""
echo "==> Authenticating with ECR..."
aws ecr get-login-password --region "${REGION}" | \
  docker login --username AWS --password-stdin \
  "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

docker tag "${PROJECT}:${IMAGE_TAG}" "${ECR_REPO}:${IMAGE_TAG}"
docker tag "${PROJECT}:${IMAGE_TAG}" "${ECR_REPO}:latest"

echo "==> Pushing ${ECR_REPO}:${IMAGE_TAG}..."
docker push "${ECR_REPO}:${IMAGE_TAG}"
docker push "${ECR_REPO}:latest"

# ── Terraform apply ───────────────────────────────────────────────────────────
echo ""
echo "==> Running terraform apply..."
cd terraform
terraform apply -var="app_image_tag=${IMAGE_TAG}" -auto-approve

# ── Smoke test hint ───────────────────────────────────────────────────────────
echo ""
echo "==> Deployment complete!"
echo "    Image : ${ECR_REPO}:${IMAGE_TAG}"
echo ""
echo "    Find the ECS task public IP:"
echo "    aws ecs list-tasks --cluster ${PROJECT}-cluster"
echo ""
echo "    Smoke test (replace <IP> with the task public IP):"
echo "    curl -X POST http://<IP>:8080/actuator/health"
echo "    curl -X POST http://<IP>:8080/check \\"
echo "      -H 'Content-Type: application/json' \\"
echo "      -d '{\"userId\":\"smoke\",\"tier\":\"FREE\",\"action\":\"api:test\"}'"
