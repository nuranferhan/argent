# ADR 0002 — One coroutine actor per symbol

## Status
Accepted

## Context
A central matching engine must be deterministic: same order stream, same trades. Concurrent mutation of a single book is a data race. Sharding by symbol is how real exchanges isolate books.

## Decision
Each symbol owns a Kotlin coroutine actor (`Channel.UNLIMITED`) and a `ConcurrentSkipListMap` book. Submit, cancel, depth, restore, and replay commands are serialized per symbol. Different symbols proceed in parallel.

## Consequences
A hot symbol is single-threaded by design (price-time FIFO). Horizontal scale is “more symbols / more processes”, not “more threads on one book”. Tests can replay the event log and assert identical depth and trade ids.
