# ── SNS topic — subscribe your email to receive alarm notifications ────────────
resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-alarms"
  tags = { Name = "${var.project_name}-alarms" }
}

# Uncomment and set your email to receive notifications:
# resource "aws_sns_topic_subscription" "email" {
#   topic_arn = aws_sns_topic.alarms.arn
#   protocol  = "email"
#   endpoint  = "your@email.com"
# }

# ── 1. ElastiCache CPU > 70% ───────────────────────────────────────────────────
# Uses the built-in AWS/ElastiCache namespace — no app changes needed.
# Fires when Redis is under pressure and latency may start spiking.
resource "aws_cloudwatch_metric_alarm" "cache_cpu_high" {
  alarm_name          = "${var.project_name}-cache-cpu-high"
  alarm_description   = "ElastiCache CPU > 70% — Redis is under pressure, latency may spike"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ElastiCache"
  period              = 60
  statistic           = "Average"
  threshold           = 70
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  dimensions = {
    CacheClusterId = aws_elasticache_cluster.redis.id
  }
}

# ── 2. Denial rate > 20% ───────────────────────────────────────────────────────
# Metric math: denied / (allowed + denied) * 100.
# Metrics come from micrometer-registry-cloudwatch2, namespace = RateLimiter.
# Uses 5-minute windows to smooth out burst spikes.
resource "aws_cloudwatch_metric_alarm" "denial_rate_high" {
  alarm_name          = "${var.project_name}-denial-rate-high"
  alarm_description   = "More than 20% of rate limit checks denied — investigate unusual traffic"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = 20
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  metric_query {
    id          = "denial_pct"
    expression  = "IF(m1+m2>0, (m2/(m1+m2))*100, 0)"
    label       = "Denial Rate %"
    return_data = true
  }

  metric_query {
    id = "m1"
    metric {
      metric_name = "ratelimit.requests.allowed"
      namespace   = "RateLimiter"
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "m2"
    metric {
      metric_name = "ratelimit.requests.denied"
      namespace   = "RateLimiter"
      period      = 300
      stat        = "Sum"
    }
  }
}

# ── 3. p99 latency > 10ms ──────────────────────────────────────────────────────
# Fires when the 99th-percentile check latency exceeds 10ms.
# The p99 value is published by Micrometer because of:
#   management.metrics.distribution.percentiles.ratelimit.check.latency=0.99
# Micrometer publishes it as metric "ratelimit.check.latency", dimension phi=0.99.
# Threshold is in seconds (Micrometer timer durations): 0.010 = 10ms.
resource "aws_cloudwatch_metric_alarm" "p99_latency_high" {
  alarm_name          = "${var.project_name}-p99-latency-high"
  alarm_description   = "p99 check latency > 10ms — Redis or JVM under pressure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ratelimit.check.latency"
  namespace           = "RateLimiter"
  period              = 60
  statistic           = "Average"
  threshold           = 0.010
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  dimensions = {
    phi = "0.99"
  }
}
