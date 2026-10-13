# ADR 0003 — Ledger locking: `SELECT FOR UPDATE` plus version column

## Status
Accepted

## Context
Holds, releases, deposits, and settlement race on the same `(user, asset)` row. Lost updates would break debit==credit.

## Decision
Balance mutations run in a PostgreSQL transaction, lock the row (`FOR UPDATE`), then `UPDATE … WHERE version = ?`. Zero rows updated raises `OptimisticLockException` and the caller retries or fails closed (hold returns false).

## Consequences
Throughput per account is serialized at the row. That is correct for cash. Idempotency keys prevent double deposit/withdraw/settlement after retries.
