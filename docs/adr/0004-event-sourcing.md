# ADR 0004 — Event log is the system of record for the book

## Status
Accepted

## Context
The in-memory book dies with the JVM. Resting orders must survive crash. Rebuilding by replaying `OrderSubmitted`/`OrderCancelled` in sequence is deterministic and testable.

## Decision
Every engine and ledger fact is appended to `domain_events`. Runtime recovery restores active orders from the `orders` table (fast path) and settles any `TradeExecuted` missing from `trades` (crash window). Admin replay rebuilds a symbol book from the event stream.

## Consequences
PostgreSQL is both ledger and event store. RabbitMQ is a fan-out bus, not the source of truth. The matching path never waits on AMQP.
