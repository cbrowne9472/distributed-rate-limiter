output "ecr_repository_url" {
  description = "Push your Docker image here: docker push <url>:<tag>"
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster — use with `aws ecs` CLI commands"
  value       = aws_ecs_cluster.main.name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL host:port"
  value       = aws_db_instance.postgres.endpoint
}

output "redis_endpoint" {
  description = "ElastiCache Redis host"
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "service_url" {
  description = "ECS task public IP — look up the running task in the console for the exact IP"
  value       = "Check ECS → Clusters → ${aws_ecs_cluster.main.name} → Tasks → public IP :8080"
}
