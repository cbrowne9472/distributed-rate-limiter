# Distributed Rate Limiter

A standalone microservice that enforces configurable rate limits across any backend. Clients send a user ID and action, and the service returns an allow/deny decision with remaining quota — keeping Redis as the single source of truth so limits hold even across multiple application instances.

---

## Architecture

```
Client Request → Your API → Rate Limiter Service → Redis
                                    │
                              Allow / Deny
                                    │
                           Continue or 429 Error
```

Rules (per-tier limits, algorithm choice) are stored in PostgreSQL and cached in Redis so every `/check` call stays sub-millisecond.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Service | Spring Boot 3.5 |
| Rate limit state | Redis 7 (sorted sets + hashes) |
| Rules storage | PostgreSQL 16 |
| Local dev | Docker Compose |
| Testing | JUnit 5 + Testcontainers |
| Metrics | Prometheus + Micrometer |
| Infrastructure | AWS ECS + ElastiCache + RDS (Terraform) |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### Run locally

```bash
# Start Redis and PostgreSQL
docker compose up -d

# Run the service
./mvnw spring-boot:run
```

The service will be available at `http://localhost:8080`.

### Run tests

```bash
./mvnw test
```

Tests use Testcontainers — Docker must be running. No manual setup needed; containers spin up and tear down automatically.

---

## Algorithms

### Sliding Window

Each request is stored as a timestamped entry in a Redis sorted set. On every `/check` call, entries older than the window are removed, and the remaining count is compared against the limit. If under the limit the request is allowed and a new entry is added — all atomically via a Lua script.

```
Key:   rate_limit:{userId}:{action}
Value: sorted set of timestamps (score = timestamp ms)
Check: ZREMRANGEBYSCORE + ZCARD + ZADD  ← single Lua script
```

**Best for:** APIs where you want a strict, evenly-enforced limit over a rolling time window.

### Token Bucket

Each user starts with a full bucket of tokens. Tokens refill at a constant rate based on the configured limit and window. Each request consumes one token. If the bucket is empty, the request is denied. State (current tokens + last refill timestamp) is stored in a Redis hash and updated atomically on every call.

```
Key:   token_bucket:{userId}:{action}
Value: hash { tokens: float, last_refill: timestamp ms }
Check: HMGET → compute refill → HMSET  ← single Lua script
```

**Best for:** APIs that need to tolerate short bursts — a user can spend saved-up tokens quickly without being penalized.

### Comparison

| Property | Sliding Window | Token Bucket |
|----------|---------------|--------------|
| Burst handling | Strict — no bursting | Allows burst up to capacity |
| Memory per key | O(requests in window) | O(1) — two fields |
| Precision | Millisecond-level | Continuous (float tokens) |
| Redis structure | Sorted set | Hash |

---

## Project Structure

```
src/
├── main/java/com/ratelimiter/
│   ├── algorithm/
│   │   ├── RateLimiterAlgorithm.java   # Interface
│   │   ├── RateLimitResult.java        # Result record (allowed, remaining, limit)
│   │   ├── SlidingWindowRateLimiter.java
│   │   ├── TokenBucketRateLimiter.java
│   │   └── RateLimiterFactory.java     # Instantiates algorithm by name
│   └── RateLimiterApplication.java
└── test/java/com/ratelimiter/
    └── algorithm/
        ├── SlidingWindowRateLimiterTest.java
        ├── TokenBucketRateLimiterTest.java
        └── RateLimiterFactoryTest.java
```

---

## Roadmap

- [x] Sliding window algorithm (Redis sorted sets)
- [x] Token bucket algorithm (Redis hashes)
- [x] Algorithm interface + factory
- [ ] PostgreSQL rules engine (per-tier limits)
- [ ] Redis caching for rules + multi-tenancy
- [ ] REST endpoints (`/check`, `/rules`, `/stats`)
- [ ] Prometheus metrics + latency histograms
- [ ] k6 load test — target 5k req/sec, sub-5ms p99
- [ ] React dashboard with live traffic chart
- [ ] AWS deployment (ECS + ElastiCache + RDS via Terraform)
- [ ] CloudWatch alarms
