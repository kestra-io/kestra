# Apache Flink Plugin for Kestra

This plugin provides tasks for orchestrating Apache Flink jobs within Kestra workflows. It supports both streaming and batch processing scenarios and integrates with Flink's REST API and SQL Gateway.

## Features

- **Submit Jobs**: Submit JAR-based jobs to Flink clusters
- **SQL Execution**: Execute SQL statements via Flink SQL Gateway
- **Job Monitoring**: Monitor job status and wait for completion
- **Job Cancellation**: Cancel running jobs with optional savepoint creation
- **Savepoint Management**: Trigger savepoints for job state preservation

## Tasks

### Submit
Submits a Flink job using a JAR file to a Flink cluster.

**Properties:**
- `restUrl`: Flink REST API URL (required)
- `jarUri`: URI of the JAR file (required)
- `entryClass`: Main class to execute (required)
- `args`: Program arguments (optional)
- `parallelism`: Job parallelism (optional)
- `restoreFromSavepoint`: Restore from savepoint path (optional)
- `allowNonRestoredState`: Allow non-restored state (default: false)
- `jobConfig`: Additional job configuration (optional)

**Example:**
```yaml
- id: submit-flink-job
  type: io.kestra.plugin.flink.Submit
  restUrl: "http://flink-jobmanager:8081"
  jarUri: "s3://flink/jars/my-job.jar"
  entryClass: "com.example.Main"
  args:
    - "--input"
    - "s3://input/data"
  parallelism: 4
```

### SubmitSql
Executes SQL statements via Flink SQL Gateway.

**Properties:**
- `gatewayUrl`: SQL Gateway URL (required)
- `statement`: SQL statement to execute (required)
- `sessionName`: Session name (optional)
- `sessionConfig`: Session configuration (optional)
- `connectionTimeout`: Connection timeout in seconds (default: 30)
- `statementTimeout`: Statement timeout in seconds (default: 300)

**Example:**
```yaml
- id: run-sql
  type: io.kestra.plugin.flink.SubmitSql
  gatewayUrl: "http://flink-sql-gateway:8083"
  statement: |
    INSERT INTO enriched_orders
    SELECT o.order_id, o.customer_id, c.name, o.amount
    FROM orders o
    JOIN customers c ON o.customer_id = c.id
  sessionConfig:
    catalog: "default_catalog"
    database: "default_database"
    configuration:
      execution.runtime-mode: "streaming"
```

### MonitorJob
Monitors a Flink job until it reaches a terminal state.

**Properties:**
- `restUrl`: Flink REST API URL (required)
- `jobId`: Job ID to monitor (required)
- `waitTimeout`: Maximum wait time (default: PT10M)
- `checkInterval`: Check interval (default: PT10S)
- `failOnError`: Fail on job error (default: true)
- `expectedTerminalStates`: Expected success states (default: ["FINISHED"])

**Example:**
```yaml
- id: monitor-job
  type: io.kestra.plugin.flink.MonitorJob
  restUrl: "http://flink-jobmanager:8081"
  jobId: "{{ outputs.submit-job.jobId }}"
  waitTimeout: "PT30M"
```

### Cancel
Cancels a running Flink job with optional savepoint creation.

**Properties:**
- `restUrl`: Flink REST API URL (required)
- `jobId`: Job ID to cancel (required)
- `withSavepoint`: Create savepoint before cancellation (default: false)
- `savepointDir`: Savepoint directory (required if withSavepoint is true)
- `drainJob`: Drain job before cancellation (default: false)
- `cancellationTimeout`: Cancellation timeout in seconds (default: 60)

**Example:**
```yaml
- id: cancel-job
  type: io.kestra.plugin.flink.Cancel
  restUrl: "http://flink-jobmanager:8081"
  jobId: "{{ inputs.jobId }}"
  withSavepoint: true
  savepointDir: "s3://flink/savepoints/canceled"
```

### TriggerSavepoint
Triggers a savepoint for a running job without canceling it.

**Properties:**
- `restUrl`: Flink REST API URL (required)
- `jobId`: Job ID (required)
- `targetDirectory`: Savepoint directory (optional)
- `cancelJob`: Cancel job after savepoint (default: false)
- `savepointTimeout`: Savepoint timeout in seconds (default: 300)
- `formatType`: Savepoint format (default: "CANONICAL")

**Example:**
```yaml
- id: create-savepoint
  type: io.kestra.plugin.flink.TriggerSavepoint
  restUrl: "http://flink-jobmanager:8081"
  jobId: "{{ inputs.jobId }}"
  targetDirectory: "s3://flink/savepoints/backup"
```

## Use Cases

### Streaming ETL Pipeline
```yaml
id: flink-streaming-etl
namespace: data.streaming

tasks:
  - id: submit-streaming-job
    type: io.kestra.plugin.flink.Submit
    restUrl: "http://flink-jobmanager:8081"
    jarUri: "s3://flink/jars/streaming-etl.jar"
    entryClass: "com.example.StreamingETL"
    args:
      - "--kafka-brokers"
      - "kafka:9092"
      - "--output-path"
      - "s3://output/streaming/"
    parallelism: 4

  - id: monitor-job
    type: io.kestra.plugin.flink.MonitorJob
    restUrl: "http://flink-jobmanager:8081"
    jobId: "{{ outputs.submit-streaming-job.jobId }}"
    waitTimeout: "PT1H"
```

### Batch Processing with Savepoints
```yaml
id: flink-batch-with-savepoint
namespace: data.batch

tasks:
  - id: submit-batch-job
    type: io.kestra.plugin.flink.Submit
    restUrl: "http://flink-jobmanager:8081"
    jarUri: "s3://flink/jars/batch-processor.jar"
    entryClass: "com.example.BatchProcessor"
    restoreFromSavepoint: "s3://flink/savepoints/latest"
    parallelism: 8

  - id: create-checkpoint
    type: io.kestra.plugin.flink.TriggerSavepoint
    restUrl: "http://flink-jobmanager:8081"
    jobId: "{{ outputs.submit-batch-job.jobId }}"
    targetDirectory: "s3://flink/savepoints/checkpoints/{{ execution.id }}"

  - id: monitor-completion
    type: io.kestra.plugin.flink.MonitorJob
    restUrl: "http://flink-jobmanager:8081"
    jobId: "{{ outputs.submit-batch-job.jobId }}"
    waitTimeout: "PT2H"
```

### SQL-Based Analytics
```yaml
id: flink-sql-analytics
namespace: analytics.sql

tasks:
  - id: create-analytics-table
    type: io.kestra.plugin.flink.SubmitSql
    gatewayUrl: "http://flink-sql-gateway:8083"
    statement: |
      CREATE TABLE daily_metrics AS
      SELECT
        DATE(event_time) as metric_date,
        event_type,
        COUNT(*) as event_count,
        AVG(event_value) as avg_value
      FROM raw_events
      WHERE event_time >= CURRENT_DATE - INTERVAL '30' DAY
      GROUP BY DATE(event_time), event_type
    sessionConfig:
      configuration:
        execution.runtime-mode: "batch"
        execution.parallelism: "16"
```

## Requirements

- Flink cluster with REST API enabled (default port 8081)
- For SQL tasks: Flink SQL Gateway (default port 8083)
- Network connectivity from Kestra to Flink cluster
- Appropriate permissions for savepoint directories (if using external storage)

## Configuration

The plugin connects to Flink using its REST API and SQL Gateway. Ensure your Flink cluster is properly configured:

1. **REST API**: Enable in flink-conf.yaml
   ```
   rest.port: 8081
   rest.address: 0.0.0.0
   ```

2. **SQL Gateway**: Enable SQL Gateway service
   ```
   sql-gateway.endpoint.rest.address: 0.0.0.0
   sql-gateway.endpoint.rest.port: 8083
   ```

3. **Savepoints**: Configure savepoint directory
   ```
   state.savepoints.dir: s3://your-bucket/savepoints
   ```