# Worker Queue Routing Cache Benchmark Results

Date: 2026-07-10
Environment: local developer machine, Java 25.0.3, Kestra backend running on `localhost:8080`.

## What Was Measured

The cache implementation optimizes the routing metadata boundary: `WorkerQueueMetaStore.DefaultWorkerQueueMetaStore`.

The benchmark compares two cases:

- `cached*`: routing snapshot is already warm, representing repeated task routing decisions inside the cache TTL.
- `uncached*`: cache is invalidated before every lookup, representing the previous repeated liveness-store read and filtering pattern.

This isolates the specific cost discussed in the design PDF: repeatedly deriving active worker queue ids from service liveness instances.

## JMH Command

```bash
./gradlew :jmh-benchmarks:jmh -Pjmh.include=io.kestra.core.runners.WorkerQueueMetaStoreBenchmark
```

The `jmh-benchmarks` Gradle module now honors the documented `-Pjmh.include=...` property.

## JMH Results

| Benchmark | Worker instances | Score | Interpretation |
| --- | ---: | ---: | --- |
| `cachedLookup` | 100 | `0.044 us/op` | Warm-cache queue availability check |
| `uncachedLookup` | 100 | `4.622 us/op` | Reload liveness instances before each check |
| `cachedLookup` | 1000 | `0.041 us/op` | Warm-cache queue availability check |
| `uncachedLookup` | 1000 | `31.154 us/op` | Reload liveness instances before each check |
| `cachedListAllWorkerQueueIds` | 100 | `0.029 us/op` | Warm-cache queue list read |
| `uncachedListAllWorkerQueueIds` | 100 | `4.739 us/op` | Reload liveness instances before each list |
| `cachedListAllWorkerQueueIds` | 1000 | `0.026 us/op` | Warm-cache queue list read |
| `uncachedListAllWorkerQueueIds` | 1000 | `31.224 us/op` | Reload liveness instances before each list |

## Improvement

| Operation | Worker instances | Speedup |
| --- | ---: | ---: |
| Queue availability lookup | 100 | `~105x` faster |
| Queue availability lookup | 1000 | `~760x` faster |
| Queue id list read | 100 | `~163x` faster |
| Queue id list read | 1000 | `~1201x` faster |

The larger speedup at 1000 workers is expected: the uncached path scales with the number of active service instances, while the cached path is a map/cache read plus a set lookup.

## Backend Smoke Benchmark

A small flow was created on the running local backend:

```yaml
id: worker-cache-benchmark
namespace: io.kestra.benchmark
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: worker cache benchmark
```

Execution command shape:

```bash
curl -u '<user>:<password>' \
  -X POST \
  -F dummy= \
  'http://localhost:8080/api/v1/main/executions/io.kestra.benchmark/worker-cache-benchmark?wait=true'
```

20 executions completed successfully.

| Sample | Count | Min | Avg | P50 | P95 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| All requests | 20 | `67.549 ms` | `151.858 ms` | `95.394 ms` | `121.538 ms` | `1181.523 ms` |
| Excluding first cold request | 19 | `67.549 ms` | `97.665 ms` | `95.394 ms` | `121.538 ms` | `121.538 ms` |

The first request was a cold outlier. The steady-state local backend sample averaged about `97.7 ms` end-to-end for this simple flow.

## Conclusion

The cache gives a clear performance benefit at the intended boundary.

The JMH benchmark shows that repeated routing checks avoid the cost of listing and filtering service instances. For 1000 active worker service instances, the availability check improves from about `31.2 us/op` to about `0.041 us/op` in the benchmark.

The backend smoke benchmark confirms the modified backend can still create and execute worker tasks successfully. It should not be used as the cache speedup number because end-to-end execution includes HTTP, auth, flow lookup, execution persistence, queueing, worker execution, and response waiting.
