# Scheduled backlog metrics

Backlog gauges report the current number of persisted executions in `CREATED` and `QUEUED` states, and unconsumed JDBC dispatch messages. They are disabled by default because refreshing them queries the repositories.

Enable them on an executor, webserver, or standalone server:

```yaml
kestra:
  metrics:
    query:
      enabled: true
      interval: 1m
```

The first sample is collected after the configured interval. Subsequent samples use the same fixed delay. Execution gauges appear after the first successful sample; queue gauges report `NaN` until then. Scraping reads the cached samples and does not run database queries. Failed queries leave the last successful sample available.

With the default `kestra` metrics prefix, Prometheus exposes:

| Metric | Labels | Value |
| --- | --- | --- |
| `kestra_executor_execution_pending` | `tenant_id`, `state` | Non-deleted, normal executions currently in `CREATED` or `QUEUED`, per tenant |
| `kestra_queue_message_pending` | `queue_name` | Unconsumed JDBC dispatch, keyed-dispatch, and vnode-dispatch messages, including all routing keys |

Execution counts use the existing execution aggregation API, so the repository backend handles the query. JDBC message counts use the configured queue datasource, including a dedicated queue database. Retained broadcast rows are excluded: they remain after consumption and cannot be treated as pending messages. Kafka's native consumer lag metrics and existing worker queue lag metrics are unchanged.

These are snapshots of shared storage, not per-process counters. When several processes expose them, use `max` across instances for the same tenant/state or queue, rather than adding duplicate samples. The default refresh period is one minute; choose the interval to balance autoscaling responsiveness against query load.
