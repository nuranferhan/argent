# ADR 0001 — Monetary amounts are `BigDecimal`, never IEEE-754

## Status
Accepted

## Context
Matching, margin, and double-entry settlement must be exact. `Double`/`Float` cannot represent 0.1 and accumulate rounding error across fills.

## Decision
All prices, quantities, notionals, balances, and PnL use `java.math.BigDecimal`. Wire format is decimal strings. Scale is applied only at instrument tick/lot boundaries (`HALF_UP`).

## Consequences
CPU cost is higher than longs. Throughput is still tens of thousands of orders/sec on a laptop actor. Correctness is the invariant of a broker ledger.
