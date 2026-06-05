# ── CloudWatch log group ───────────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = 7
  tags              = { Name = "${var.project_name}-logs" }
}

# ── ECS Cluster ────────────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = { Name = "${var.project_name}-cluster" }
}

# ── Task Definition ────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "app" {
  family                   = var.project_name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"   # 0.5 vCPU
  memory                   = "1024"  # 1 GB
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = var.project_name
    image = "${aws_ecr_repository.app.repository_url}:${var.app_image_tag}"

    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]

    # Spring Boot env vars — wired directly to the Terraform-managed endpoints
    environment = [
      {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/ratelimiter"
      },
      {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = "postgres"
      },
      {
        name  = "SPRING_DATASOURCE_PASSWORD"
        value = var.db_password
      },
      {
        name  = "SPRING_DATA_REDIS_HOST"
        value = aws_elasticache_cluster.redis.cache_nodes[0].address
      },
      {
        name  = "SPRING_DATA_REDIS_PORT"
        value = "6379"
      },
      {
        name  = "SPRING_JPA_HIBERNATE_DDL_AUTO"
        value = "none"
      },
      {
        name  = "MANAGEMENT_CLOUDWATCH_METRICS_EXPORT_ENABLED"
        value = "true"
      },
      {
        name  = "MANAGEMENT_CLOUDWATCH_METRICS_EXPORT_NAMESPACE"
        value = "RateLimiter"
      },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = { Name = var.project_name }
}

# ── ECS Service ────────────────────────────────────────────────────────────────
resource "aws_ecs_service" "app" {
  name            = "${var.project_name}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true  # Tasks get a public IP — add an ALB to go production-grade
  }

  # Ensure the execution role is ready before the service starts
  depends_on = [aws_iam_role_policy_attachment.ecs_execution]

  tags = { Name = "${var.project_name}-service" }
}
