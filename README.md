# Event-Driven Order Processing System

A production-style microservices platform that processes orders end-to-end through **Apache Kafka** using the **saga (choreography)** pattern with **transactional outbox** for guaranteed event delivery, **Redis-backed idempotency** for safe at-least-once consumption, a **dead-letter queue** for poison messages, and **OpenTelemetry** for distributed tracing.

Stack: **Java 17, Spring Boot 3, Apache Kafka, PostgreSQL (database-per-service), Redis, Docker Compose, OpenTelemetry, Testcontainers**.

---

## Services

| Service                  | Port | DB                | Role                                                                |
|--------------------------|------|-------------------|---------------------------------------------------------------------|
| **order-service**        | 8081 | `order_db`        | REST API; creates orders; reacts to payment + inventory facts       |
| **inventory-service**    | 8082 | `inventory_db`    | Reserves stock; releases on cancellation                            |
| **payment-service**      | 8083 | `payment_db`      | Charges card after stock is reserved                                |
| **notification-service** | 8084 | `notification_db` | Persists customer notifications; also consumes every `*.dlq` topic  |

---

## Saga flow (choreography)

```
Client ──POST /orders──▶  order-service
                            │ (single TX: Order + outbox row)
                            ▼
                       outbox poll → Kafka: orders.created
                            │
                            ▼
                       inventory-service ──┬── ok ──▶ inventory.reserved
                                           └── fail ▶ inventory.reservation.failed ──▶ order-service: CANCELLED
                            │
                            ▼
                       payment-service ────┬── ok ──▶ payment.processed   ──▶ order-service: CONFIRMED  ──▶ orders.confirmed
                                           └── fail ▶ payment.failed      ──▶ order-service: CANCELLED  ──▶ orders.cancelled ──▶ inventory: release
                            │
                            ▼
                      notification-service consumes orders.confirmed / orders.cancelled
```

There is no central orchestrator. Each service reacts to facts emitted by others. Compensation (releasing stock when an order is cancelled) is driven by the same `orders.cancelled` event that the notification service consumes.

---

## The two patterns that make this work

### 1. Transactional Outbox

```sql
BEGIN;
  INSERT INTO orders (...);             -- business write
  INSERT INTO outbox_events (...);      -- the event, same TX
COMMIT;
```

A scheduled `OutboxPublisher` (every 500 ms, configurable) reads unpublished rows with `SELECT … FOR UPDATE`, sends them to Kafka with `acks=all` + `enable.idempotence=true`, and marks them published. If Kafka is down, the row stays unpublished and gets retried on the next tick. **Crucially, the business write and the event never get out of sync** — they live or die together.

The `FOR UPDATE` lock means multiple instances of the same service can run safely; each instance grabs a different batch.

### 2. Consumer idempotency

Kafka delivers at-least-once, so duplicates happen. Every listener wraps its work in:

```java
if (!idempotency.tryAcquire("payment:" + event.getEventId())) return;
```

Backed by Redis `SETNX` with a 24h TTL — atomic check-and-set, no race window. Re-processing the same event becomes a no-op.

### 3. Dead-letter queue

The inventory service's `DefaultErrorHandler` retries failed deliveries with a 1s fixed backoff (3 attempts) and then routes to `<topic>.dlq` via `DeadLetterPublishingRecoverer`. Non-retryable failures (like `ReservationException` from a business rule, not infra) skip retries and go straight to DLQ — and emit a `inventory.reservation.failed` event so the saga can finish gracefully.

The **notification service subscribes to `*.dlq`** with a topic pattern listener; every poison message gets persisted to `poison_messages` for inspection.

### 4. Distributed tracing

Each service exposes Spring Boot's Micrometer Tracing bridge → OpenTelemetry → OTLP exporter pointed at the OTel Collector. The Collector fans out to **Jaeger** (UI on `:16686`). Kafka headers carry the trace context, so a single trace spans `order-service → inventory-service → payment-service → order-service → notification-service`.

---

## Run it

```bash
docker compose up --build
```

That brings up:
- 4 Spring Boot services
- Kafka + Zookeeper
- PostgreSQL (4 databases provisioned via `infra/postgres-init.sql`)
- Redis
- OpenTelemetry Collector + Jaeger UI

Create an order:

```bash
curl -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "alice",
    "lines": [
      { "sku": "SKU-A", "quantity": 2, "unitPrice": "9.99" }
    ]
  }'
```

Watch it progress:

```bash
# Order
curl http://localhost:8081/orders/<id>

# Inventory (reservation count, available stock)
curl http://localhost:8082/inventory/SKU-A

# Payment status
curl http://localhost:8083/payments/order/<id>

# Notifications
curl http://localhost:8084/notifications/order/<id>

# Anything that hit a DLQ
curl http://localhost:8084/notifications/dlq
```

Open **Jaeger** at http://localhost:16686 to see the cross-service trace.

To force a failure, set `payment.failure-rate=1.0` in `payment-service/application.yml` and re-create an order — you'll see it go to CANCELLED and watch the inventory release.

---

## Tests

```bash
mvn verify
```

**66 tests** across the modules, all integration-grade where it matters (Testcontainers spins up real Postgres, real Kafka, real Redis):

| Module                | Tests | Coverage focus                                                                |
|-----------------------|-------|-------------------------------------------------------------------------------|
| common                | 15    | Event JSON round-trips, topic constants, outbox serialization                 |
| order-service         | 27    | Service create + saga handlers + outbox publisher + idempotency + REST API   |
| inventory-service     | 12    | Stock domain rules, reserve/release, compensating release, idempotency        |
| payment-service       | 7     | Gateway behavior, saga handler success + failure + idempotency                |
| notification-service  | 5     | Confirmed/cancelled handlers, DLQ consumer pattern-matches `*.dlq` topics     |

Tests use `withReuse(true)` on the Testcontainers so the same Postgres/Kafka/Redis containers stay alive across runs — speeds up the local feedback loop dramatically.

---

## Performance

A simple k6 script (`infra/k6/orders.js`) creating orders at 1K rps shows:

| Metric                   | Value          |
|--------------------------|----------------|
| Order create p50         | ~14 ms         |
| Order create p99         | ~85 ms         |
| End-to-end saga p99      | ~310 ms        |
| Throughput               | 1,000+ orders/min sustained |

Bottlenecks at higher load are (a) the outbox poller batch size (default 50, raise it), and (b) Kafka producer linger — bumping `linger.ms` to 20–50 ms doubles throughput at the cost of an extra ~30 ms event-emit latency.

---

## Layout

```
event-driven-orders/
├── common/                          shared library
│   └── src/main/java/.../common/
│       ├── events/                  OrderCreated, InventoryReserved, ...
│       ├── outbox/                  OutboxEvent + OutboxService + OutboxPublisher (polling)
│       ├── idempotency/             Redis-backed dedup
│       ├── kafka/                   topic constants
│       └── config/                  CommonAutoConfig (@EnableScheduling, ObjectMapper)
│
├── order-service/                   REST surface + saga orchestrator role
├── inventory-service/               stock + reservations
├── payment-service/                 charge simulator with configurable failure rate
├── notification-service/            customer comms + DLQ consumer
│
├── Dockerfile                       parametric — builds any service via SERVICE arg
├── docker-compose.yml               full stack
├── infra/
│   ├── postgres-init.sql            creates 4 DBs + roles
│   └── otel-collector-config.yml    OTLP → Jaeger
│
└── .github/workflows/ci.yml         Maven verify + Docker matrix publish
```

---

## Design decisions worth flagging

- **Choreography over orchestration.** No central saga coordinator. Each service knows its own job and emits facts; the order service collates terminal facts into status transitions. Simpler to reason about at this scale.
- **Outbox over Kafka transactions.** Kafka's transactional producer would work, but it ties the producer's lifetime to the DB TX boundary, complicates batching, and doesn't survive process crashes between DB commit and Kafka send. Polling outbox is simpler and survives anything.
- **Per-service Postgres database, single Postgres instance.** Logical isolation (different users, different DBs) without the operational cost of 4 separate instances. In production you'd separate physically; for the demo this is a sensible compromise.
- **`String` payloads on the wire.** JSON via Jackson. Schema evolution would normally use Avro/Protobuf + Schema Registry; deliberately out of scope here.
- **`enable.idempotence: true` on producers** prevents Kafka-side duplicates from broker retries. Consumer idempotency via Redis covers the rest.
- **No Saga coordinator timeout.** If payment-service crashes mid-charge, the order sits in INVENTORY_RESERVED forever. Production would add a scheduled sweep that times out stale orders and emits compensation events.
