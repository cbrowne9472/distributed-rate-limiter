# Distributed Rate Limiter

A production-grade rate limiting microservice built with Spring Boot and Redis. Any backend can call it to ask "should I allow this request?" — it returns an allow/deny decision with remaining quota in under 5ms, with limits enforced consistently across any number of application instances.

Real companies (Stripe, AWS, Cloudflare) run this exact pattern internally. This project implements it end to end: two algorithms, multi-tenant rule management, a live monitoring dashboard, and a full AWS deployment.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          Your Backend                           │
│                                                                 │
│   Incoming Request  →  POST /check  →  Allow / Deny (429)      │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────▼────────────┐
                    │   Rate Limiter Service  │   Spring Boot 3.5
                    │                        │
                    │  1. Look up rule        │◄──── Redis cache (5 min TTL)
                    │  2. Pick algorithm      │
                    │  3. Atomic check        │◄──── Redis (sorted set / hash)
                    │  4. Return result       │
                    └────────────────────────┘
                          │           │
              ┌───────────┘           └───────────┐
              ▼                                   ▼
   ┌──────────────────┐                ┌──────────────────┐
   │  PostgreSQL 16   │                │    Redis 7        │
   │                  │                │                   │
   │  Rate limit rules│                │  Sliding window:  │
   │  (tier + limits) │                │  sorted set of    │
   │  FREE:  100/min  │                │  timestamps       │
   │  PRO:  1000/min  │                │                   │
   │  INTERNAL: ∞     │                │  Token bucket:    │
   └──────────────────┘                │  hash{tokens,     │
                                       │  last_refill}     │
                                       └──────────────────┘
```

See `architecture.drawio` for the full AWS deployment diagram (open with [diagrams.net](https://app.diagrams.net)).

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Service | Spring Boot 3.5, Java 21 |
| Rate limit state | Redis 7 (sorted sets + hashes, Lua scripts) |
| Rules storage | PostgreSQL 16 + Flyway migrations |
| Caching | Spring Cache + Redis (5-min TTL) |
| Frontend | React + Vite + Recharts |
| Load testing | k6 |
| Metrics | Prometheus + Micrometer (+ CloudWatch in AWS) |
| Infrastructure | AWS ECS Fargate + ElastiCache + RDS (Terraform) |
| Testing | JUnit 5 + Testcontainers |

---

## Load Test Results

Tested with [k6](https://k6.io) on a MacBook Pro (Apple Silicon) against a local Docker stack.
500 rotating user IDs across 3 tiers (FREE / PRO / INTERNAL), 4 random actions — ramped from 100 to **5,000 req/sec** and held for 60 seconds.

| Metric              | Result                                             |
|---------------------|----------------------------------------------------|
| **Peak throughput** | **5,000 req/sec** (sustained)                      |
| **p50 latency**     | **2.57 ms**                                        |
| **p95 latency**     | **6.45 ms**                                        |
| **p99 latency**     | **21.9 ms**                                        |
| Total requests      | 460,356 over 2m 20s                                |
| Server errors (5xx) | **0**                                              |
| Rate-limit denials  | 19.7% — expected, rotating users exhaust FREE quota |

All k6 thresholds passed: `p(50) < 10ms` ✓  `p(95) < 30ms` ✓  `p(99) < 100ms` ✓

---

## Algorithms

### Sliding Window

Each request is stored as a timestamped entry in a Redis sorted set. On every `/check` call, entries older than the window are removed, then the remaining count is compared against the limit. If under the limit the request is allowed and a new entry is added — all atomically via a single Lua script.

```
Key:   rate_limit:{userId}:{action}
Value: sorted set of timestamps (score = epoch ms)
Check: ZREMRANGEBYSCORE + ZCARD + ZADD  ← single Lua script, atomic
```

**Best for:** APIs where you need a strict, evenly-enforced limit over a rolling time window. No spikes allowed at window boundaries.

### Token Bucket

Each user starts with a full bucket of tokens that refill at a constant rate. Each request consumes one token. If the bucket is empty, the request is denied. State (token count + last refill timestamp) lives in a Redis hash and is updated atomically on every call.

```
Key:   token_bucket:{userId}:{action}
Value: hash { tokens: float, last_refill: epoch ms }
Check: HMGET → compute refill → conditional HMSET  ← single Lua script
```

**Best for:** APIs that need to tolerate short bursts — a user can spend accumulated tokens quickly without being rate limited, as long as their average rate stays within the limit.

### Comparison

| Property             | Sliding Window        | Token Bucket                |
|----------------------|-----------------------|-----------------------------|
| Burst handling       | Strict — no bursting  | Allows burst up to capacity |
| Memory per key       | O(requests in window) | O(1) — two fields           |
| Precision            | Millisecond-level     | Continuous (float tokens)   |
| Redis data structure | Sorted set            | Hash                        |
| Good for             | Strict APIs           | User-facing products        |

---

## Multi-Tenancy

Rate limit rules are stored in PostgreSQL and looked up by priority:

1. **User + action** — most specific, e.g. "user-123 can make 50 /upload calls per minute"
2. **Tier + action** — e.g. "all PRO users get 500 /api calls per minute"
3. **Tier-wide** — e.g. "all FREE users get 100 requests per minute on any action"

Rules are cached in Redis (5-minute TTL) so `/check` calls almost never hit PostgreSQL. Default seed rules:

| Tier     |     Limit | Window |      Algorithm |
|----------|-----------|--------|----------------|
| FREE     | 100 req   | 60 sec | Sliding window |
| PRO      | 1,000 req | 60 sec | Sliding window |
| INTERNAL | Unlimited | —      | —              |

---

## API

### `POST /check`
Ask whether a request should be allowed.

```json
// Request
{ "userId": "user-123", "action": "api_call", "tier": "FREE" }

// Response 200 — allowed
{ "allowed": true, "remaining": 94, "limit": 100, "windowSeconds": 60 }

// Response 429 — denied
{ "allowed": false, "remaining": 0, "limit": 100, "windowSeconds": 60 }
```

### `POST /rules`
Create or update a rate limit rule.

```json
{ "tier": "PRO", "action": "upload", "limitCount": 50, "windowSeconds": 60, "algorithmType": "sliding_window" }
```

### `GET /stats/{userId}`
Current usage for a user.

```json
{ "userId": "user-123", "action": "api_call", "currentCount": 6, "remaining": 94, "limit": 100 }
```

### `GET /actuator/prometheus`
Prometheus metrics endpoint. Exposes:
- `ratelimit_requests_allowed_total` — tagged by userId, action, tier
- `ratelimit_requests_denied_total` — tagged by userId, action, tier
- `ratelimit_redis_latency_seconds` — Redis round-trip time
- `ratelimit_check_latency_seconds` — total check latency including rule lookup

---

## Running Locally

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker + Docker Compose
- Node 18+ (for the frontend)

### Start the backend

```bash
# Start Redis and PostgreSQL
docker compose up -d

# Run the service
./mvnw spring-boot:run
```

The service starts at `http://localhost:8080`. Flyway runs automatically and seeds the default tier rules on first start.

### Start the dashboard

```bash
cd frontend
npm install
npm run dev
```

Dashboard at `http://localhost:5173`. Hit **Fire 200 Requests** to see the rate limiter in action — the chart will show requests coming in and denials appearing once the FREE tier limit (100/min) is exhausted.

### Run tests

```bash
./mvnw test
```

All 36 tests use Testcontainers — Docker must be running. No manual setup needed.

### Run the load test

```bash
# k6 must be installed: brew install k6
k6 run load-test/load-test.js
```

---

## Deploying to AWS

Infrastructure is defined in `terraform/`. It creates: VPC, ECS Fargate cluster, ElastiCache Redis, RDS PostgreSQL, ECR repository, IAM roles, and CloudWatch alarms.

```bash
# 1. Set your DB password
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
# Edit terraform.tfvars and set db_password

# 2. Build and push the Docker image, then apply infrastructure
./deploy.sh
```

`deploy.sh` handles ECR authentication, Docker build, image push, and `terraform apply` in sequence.

**To tear everything down:**
```bash
cd terraform && terraform destroy -auto-approve
```

### CloudWatch Alarms

Three alarms are provisioned automatically:

| Alarm | Threshold | What it means |
|-------|-----------|---------------|
| `denial-rate-high` | > 20% of requests denied | Unusual traffic spike or misconfigured limits |
| `p99-latency-high` | p99 > 10ms | Redis latency degraded — check ElastiCache |
| `cache-cpu-high` | ElastiCache CPU > 70% | Cache under pressure — consider scaling |

---

## Project Structure

```
├── src/main/java/com/ratelimiter/
│   ├── algorithm/
│   │   ├── RateLimiterAlgorithm.java        # Interface
│   │   ├── RateLimitResult.java             # Result record
│   │   ├── SlidingWindowRateLimiter.java    # Redis sorted set + Lua
│   │   ├── TokenBucketRateLimiter.java      # Redis hash + Lua
│   │   └── RateLimiterFactory.java          # Selects algorithm by name
│   ├── controller/
│   │   ├── RateLimitController.java         # /check, /rules, /stats
│   │   └── DashboardController.java         # /dashboard/metrics
│   ├── dto/                                 # Request/response records
│   ├── model/
│   │   ├── RateLimitRule.java               # JPA entity
│   │   └── UserTier.java                    # FREE / PRO / INTERNAL
│   ├── repository/RateLimitRuleRepository.java
│   ├── service/
│   │   ├── RateLimitRuleService.java        # Rule lookup with @Cacheable
│   │   └── RateLimitCheckService.java       # Orchestrates check + metrics
│   └── config/
│       ├── CacheConfig.java                 # Redis cache manager
│       └── WebConfig.java                   # CORS configuration
├── frontend/                                # React + Vite dashboard
├── load-test/load-test.js                   # k6 load test script
├── terraform/                               # AWS infrastructure as code
│   ├── vpc.tf, ecs.tf, rds.tf
│   ├── elasticache.tf, ecr.tf
│   ├── iam.tf, security.tf
│   └── cloudwatch.tf
├── docker-compose.yml
└── Dockerfile                               # Two-stage build (Maven → JRE)
```