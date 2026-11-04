#!/usr/bin/env python3
"""Warn on matching-engine JMH regressions greater than 20% vs checked-in baseline."""

import json
import re
import sys
from pathlib import Path


BASELINE = Path("docs/benchmarks/baseline.md")
RESULTS = Path("argent-engine/build/reports/jmh/results.json")
BENCHMARK = "addLimitOrderToBookOfConfiguredSize"
LIMIT = 0.20


def baseline_values():
    text = BASELINE.read_text(encoding="utf-8")
    values = {}
    for line in text.splitlines():
        match = re.match(
            r"\|\s*([\d,]+) resting orders\s*\|\s*([\d.]+) µs/op\s*\|"
            r"\s*[\d.]+ µs/op\s*\|\s*[\d.]+ µs/op\s*\|\s*[\d.]+ µs/op\s*\|",
            line,
        )
        if match:
            size, mean = match.groups()
            p99 = re.search(r"\|\s*([\d.]+) µs/op\s*\|\s*[\d,]+\s*\|", line)
            if p99:
                values[int(size.replace(",", ""))] = (float(mean), float(p99.group(1)))
    return values


def main():
    if not RESULTS.exists():
        print(f"Benchmark result missing: {RESULTS}", file=sys.stderr)
        return 2
    baseline = baseline_values()
    if not baseline:
        print(f"No comparable baseline rows found in {BASELINE}", file=sys.stderr)
        return 2
    results = json.loads(RESULTS.read_text(encoding="utf-8"))
    measured = {}
    for result in results:
        if not result.get("benchmark", "").endswith(BENCHMARK):
            continue
        params = result.get("params", {})
        size = int(params.get("orderCount", 0))
        metric = result.get("primaryMetric", {})
        score = float(metric.get("score", 0))
        unit = metric.get("scoreUnit", "")
        percentiles = metric.get("scorePercentiles", {})
        p99 = percentiles.get("99.0", percentiles.get("99", score))
        # JMH sample-time results are reported in us/op, matching the checked-in baseline.
        if size and unit in {"us/op", "µs/op"}:
            measured[size] = (score, float(p99))
    if not measured:
        print("Comparable fixed-depth sample-time benchmark results were not produced.", file=sys.stderr)
        return 2
    regressions = []
    for size, (mean, p99) in sorted(measured.items()):
        if size not in baseline:
            continue
        base_mean, base_p99 = baseline[size]
        mean_change = mean / base_mean - 1 if base_mean else 0
        p99_change = p99 / base_p99 - 1 if base_p99 else 0
        print(
            f"{size} orders: mean {mean:.3f} us/op ({mean_change:+.1%}); "
            f"p99 {p99:.3f} us/op ({p99_change:+.1%})"
        )
        if mean_change > LIMIT or p99_change > LIMIT:
            regressions.append((size, mean_change, p99_change))
    if regressions:
        details = "; ".join(
            f"{size} orders mean={mean_change:+.1%}, p99={p99_change:+.1%}"
            for size, mean_change, p99_change in regressions
        )
        print(f"::warning title=Matching benchmark regression::{details}")
        print("Warning only: JMH variance is hardware-sensitive; review on comparable runners.")
    else:
        print("No measured mean or p99 regression exceeds 20% of the documented baseline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
