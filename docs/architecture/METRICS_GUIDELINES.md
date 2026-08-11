# Metrics Guidelines

Conventions for adding or changing metrics that are exposed to Prometheus.

## Standards

- **Exposition format:** [OpenMetrics 1.0](https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md) (2020) — the standardized superset of the Prometheus text format. Prometheus consumes both.
- **Library:** [Micrometer](https://micrometer.io/) via the `MeterRegistry` API. Micrometer translates its dot-notation names into Prometheus snake_case at scrape time.
- **Reference doc:** [Prometheus naming best practices](https://prometheus.io/docs/practices/naming/).

## TL;DR

> **Every Counter — without exception — MUST end in `.total`.** No Counter ships without it. No Gauge, Timer, or DistributionSummary ever uses `.total`. If you take one rule away from this document, take this one.

## Metric types

| Type | Required suffix | When to use | Examples |
|---|---|---|---|
| `Counter` | **`.total`** (always) | Monotonically increasing total. Resets only on process restart. | `controller.job.dispatched.total`, `queue.message.emitted.total` |
| `Gauge` | none — never `.total` or `.count` | Snapshot value that can go up or down. | `controller.worker.active`, `queue.subscribers` |
| `Timer` | `.duration` (or unit) | Duration distribution. Emits `_count`, `_sum`, `_max`, `_bucket` automatically. | `task.execution.duration` |
| `DistributionSummary` | unit (e.g. `.bytes`) | Non-time distribution. Same auto-fields as Timer. | `queue.message.size.bytes` |

Pick the type from the **semantics**, not from what's easiest to read off a dashboard. A "running count" that decrements is a Gauge, not a Counter.

## Naming

Micrometer name → Prometheus name translation: dots become underscores, and Prometheus appends suffixes based on type. Plan the final Prometheus name, not the Micrometer one.

### Rules

1. **Use dot.notation in Micrometer**, lowercase, words separated by dots: `worker.job.running`, `executor.taskrun.ended`.
2. **Order is `<system>.<subject>.<qualifier>`** — subsystem first, then the *thing* being measured, then state/adjective/scope. `controller.worker.active`, not `controller.active.worker`. `queue.message.big`, not `queue.big.message`. This makes related metrics group together when the registry is sorted alphabetically (all `controller.worker.*` adjacent), and makes greps for a subject return all its metrics.
3. **Every Counter ends in `.total`. No exceptions.**
   - Micrometer name: `controller.job.dispatched.total`
   - Java constant: `METRIC_CONTROLLER_JOB_DISPATCHED_TOTAL`
   - Prometheus exposed: `controller_job_dispatched_total`
   - Description: usually starts with "The total number of …"

   Never `.count`. Never bare (no suffix). The `.total` suffix is the OpenMetrics canonical marker for a cumulative counter; baking it into the Micrometer name keeps the Java constant name, the Micrometer meter name, and the Prometheus exposed name in 1:1 alignment, so there is no ambiguity when grepping or reviewing code. Recent Micrometer versions (1.10+) detect the existing `_total` and avoid double-suffixing on export.

   If you find yourself reaching for any other suffix on a Counter, the metric is probably actually a Gauge — re-read [Metric types](#metric-types) above.
4. **Gauges: noun, no `_count` / `_total` suffix.** A gauge *is* a count by nature; the suffix is noise. Prefer `worker.running` over `worker.running.count`. `.total` is reserved for Counters — if a gauge needs to express "across all groups / cluster-wide", use `.all`, `.global`, or restructure as a tagged metric and aggregate via `sum()` at query time.
5. **Timers: suffix with the unit or `duration`** when the base name does not already imply time: `task.execution.duration`. Do not add `.count` — the timer emits `_count` itself.
6. **Use a unit suffix when the unit is not obvious**: `_bytes`, `_seconds`, `_ratio`. Use base units (seconds, bytes), not milliseconds or kilobytes — Micrometer/Prometheus assume base units.
7. **Be consistent with verb tense.** Pick `ended` or `end` project-wide and stick to it. Mixing them across the same subsystem is a smell.
8. **Don't mix separators.** `queue.big_message.count` mixes dots and underscores — use `queue.message.big` (and drop the `.count`).
9. **The Java constant name should match the metric.** `METRIC_WORKER_JOB_THREAD_COUNT` for a gauge named `worker.job.thread` is misleading; rename the constant to drop `COUNT` if the metric is a gauge. Apply the same `<system>.<subject>.<qualifier>` ordering to the constant: `METRIC_CONTROLLER_WORKER_ACTIVE` mirrors `controller.worker.active`.

### Examples

| ✅ Good | ❌ Bad | Why |
|---|---|---|
| `http.server.requests.total` (Counter) | `http.server.requests` or `http.server.requests.count` | Counters must end in `.total` |
| `worker.running` (Gauge) | `worker.running.count` or `worker.running.total` | Gauges don't take `_count` or `.total` |
| `task.execution.duration` (Timer) | `task.execution.duration.count` | Timer auto-emits `_count` |
| `queue.message.size.bytes` | `queue.message.size.kb` | Use base units |
| `sla.violation` | `sla.expired` *and* `sla.violation` with the same description | Duplicate descriptions = same metric, pick one |

## Labels (tags)

- **Keep cardinality bounded.** Every unique label-value combination is a separate time series. Avoid labels that take user-controlled or unbounded values (execution IDs, full URLs, free-form input).
- **Acceptable label sources:** flow/namespace/tenant identifiers (bounded by the operator), enum-valued status fields, kestra component names.
- **Don't put data in metric names that belongs in labels.** `worker_running_us_east_1` should be `worker_running{region="us-east-1"}`.
- **Use the shared global tags** from `GlobalTagsConfigurer` rather than re-tagging at every call site.

## Help text / descriptions

- Every meter must have a `description`. It becomes the `# HELP` line in Prometheus exposition.
- One sentence, present tense, no trailing period required but be consistent.
- **Two metrics with the same description is a bug** — either they are the same metric (merge them) or one description is wrong.

## Units

- **Time:** seconds. Micrometer's `Timer` records nanoseconds internally and exports seconds — let it; do not pre-convert.
- **Bytes:** bytes, not KB/MB.
- **Ratios:** `0.0`–`1.0`, suffix `_ratio`. Don't expose percentages (0–100).

## Anti-patterns observed in `MetricRegistry.java`

These are the recurring smells; avoid reintroducing them:

1. Manual `.count` suffix (use `.total` on Counters; nothing on Gauges).
2. Counter without `.total` suffix.
3. `.total` suffix on a Gauge (use `.all` / `.global` for cross-group rollups, or aggregate via `sum()` at query time).
4. Dot/underscore mixing in a single name.
5. Two metrics sharing a description (`sla.expired` and `sla.violation`).
6. Inconsistent verb tense across sibling metrics (`end` vs `ended`).
7. Java constants whose name implies a different metric type than the metric actually has.

## Checklist before adding a metric

- [ ] Type matches the semantics (Counter/Gauge/Timer/Summary)?
- [ ] **If it's a Counter, does the name end in `.total`?** (mandatory — see rule 3)
- [ ] If it's a Gauge, does the name *not* end in `.total` (and not in `.count`)?
- [ ] Final Prometheus name reads as `<system>_<subject>_<qualifier>(_total)`?
- [ ] No `_count` suffix anywhere (Timer auto-emits it)?
- [ ] Unit is a base unit and present in the name when not obvious?
- [ ] All labels have bounded cardinality?
- [ ] Description is unique and informative?
- [ ] Java constant name matches the metric name and type?

## Changing an existing metric

Renaming or retyping a metric is a **breaking change** for anyone whose dashboards or alerts depend on it. Default migration:

1. Register the new metric alongside the old one. Mark the old one as `@Deprecated` in code and note the replacement in its description.
2. Ship one release with both. Communicate the rename in release notes.
3. Drop the old metric in the following release.

Skip the deprecation window only when you are certain no dashboard or alert references the metric (typically: brand-new metrics in the same release that introduced them).
