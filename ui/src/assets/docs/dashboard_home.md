# Custom Dashboards as Code

Build custom dashboards to track workflow executions, logs, metrics, flows, and triggers — filtered by namespace, label, or any supported field.

The declarative syntax lets you manage dashboards as code alongside your flows.

## Dashboard structure

A dashboard definition has four top-level properties:

| Property | Required | Description |
| --- | --- | --- |
| `id` | ✅ | Unique identifier for the dashboard. |
| `title` | ✅ | Display name shown in the UI. |
| `description` | ❌ | Optional description of the dashboard. |
| `timeWindow` | ❌ | Controls the selectable date range for the dashboard. |
| `charts` | ❌ | List of chart definitions. |

### `timeWindow`

| Property | Default | Description |
| --- | --- | --- |
| `default` | `P30D` | Default time range applied when the dashboard opens. ISO-8601 duration, max 366 days. |
| `max` | `P366D` | Maximum selectable range. ISO-8601 duration, max 366 days. |

## Chart types

Each chart in `charts` requires an `id`, a `type`, and a `chartOptions` block.

| Type | Description |
| --- | --- |
| `io.kestra.plugin.core.dashboard.chart.TimeSeries` | Track values over time. Supports up to two aggregated columns. |
| `io.kestra.plugin.core.dashboard.chart.Bar` | Compare categorical data. Supports one aggregated column. |
| `io.kestra.plugin.core.dashboard.chart.Pie` | Show proportions and distributions. Supports one aggregated column. |
| `io.kestra.plugin.core.dashboard.chart.Table` | Display structured data in a sortable table. |
| `io.kestra.plugin.core.dashboard.chart.KPI` | Display a single numeric or percentage value. |
| `io.kestra.plugin.core.dashboard.chart.Markdown` | Add static or flow-sourced Markdown text to the dashboard. |

### `chartOptions`

All chart types share these `chartOptions` properties:

| Property | Required | Default | Description |
| --- | --- | --- | --- |
| `displayName` | ✅ | — | Label shown as the chart title. |
| `description` | ❌ | — | Optional subtitle shown below the title. |
| `width` | ❌ | `6` | Grid width from `1` to `12`. The dashboard uses a 12-column grid. |

The `KPI` chart type adds:

| Property | Default | Description |
| --- | --- | --- |
| `numberType` | `FLAT` | Display the value as a raw count (`FLAT`) or a percentage (`PERCENTAGE`). |

## Data sources

All chart types except `Markdown` require a `data` block with a `type` and a `columns` map.

### Standard data sources

Use with `TimeSeries`, `Bar`, `Pie`, and `Table`.

| Type | Description |
| --- | --- |
| `io.kestra.plugin.core.dashboard.data.Executions` | Flow execution records. |
| `io.kestra.plugin.core.dashboard.data.Flows` | Flow definitions. |
| `io.kestra.plugin.core.dashboard.data.Logs` | Execution log entries. |
| `io.kestra.plugin.core.dashboard.data.Metrics` | Metrics emitted during executions. |
| `io.kestra.plugin.core.dashboard.data.Triggers` | Trigger records. |
| `io.kestra.plugin.ee.dashboard.data.Assets` | (Enterprise Edition) Asset inventory. Not filtered by the dashboard time range — charts always reflect the current inventory. |

### KPI data sources

Use with the `KPI` chart type.

| Type | Description |
| --- | --- |
| `io.kestra.plugin.core.dashboard.data.ExecutionsKPI` | Execution data for KPI calculations. |
| `io.kestra.plugin.core.dashboard.data.FlowsKPI` | Flow data for KPI calculations. |
| `io.kestra.plugin.core.dashboard.data.LogsKPI` | Log data for KPI calculations. |
| `io.kestra.plugin.core.dashboard.data.MetricsKPI` | Metrics data for KPI calculations. |

## Column properties

Each entry in the `columns` map defines one column or measure.

| Property | Description |
| --- | --- |
| `field` | The data source field to use. Required for dimension columns; optional for pure aggregation columns. |
| `displayName` | Label shown in the chart for this column. |
| `agg` | Aggregation function: `AVG`, `COUNT`, `MAX`, `MIN`, `SUM`. |
| `graphStyle` | Graph style for `TimeSeries` charts: `LINES`, `BARS`, `POINTS`. |
| `columnAlignment` | Column alignment for `Table` charts: `LEFT`, `RIGHT`, `CENTER`. |

## Available fields by data source

### Executions / ExecutionsKPI

`ID`, `NAMESPACE`, `FLOW_ID`, `FLOW_REVISION`, `STATE`, `DURATION`, `LABELS`, `START_DATE`, `END_DATE`, `TRIGGER_EXECUTION_ID`, `SCOPE`

### Flows / FlowsKPI

`ID`, `NAMESPACE`, `REVISION`

### Logs / LogsKPI

`NAMESPACE`, `FLOW_ID`, `EXECUTION_ID`, `TASK_ID`, `TASK_RUN_ID`, `TRIGGER_ID`, `ATTEMPT_NUMBER`, `DATE`, `LEVEL`, `MESSAGE`

Note: `MESSAGE` cannot be used as an aggregation field.

### Metrics / MetricsKPI

`NAMESPACE`, `FLOW_ID`, `TASK_ID`, `EXECUTION_ID`, `TASK_RUN_ID`, `TYPE`, `NAME`, `VALUE`, `DATE`

### Triggers

`ID`, `NAMESPACE`, `FLOW_ID`, `TRIGGER_ID`, `EXECUTION_ID`, `NEXT_EXECUTION_DATE`, `WORKER_ID`

### Assets (Enterprise Edition)

`ID`, `TYPE`, `NAMESPACE`, `DISPLAY_NAME`, `METADATA`, `CREATED`, `UPDATED`

The `METADATA` field supports a `key` property to target a specific metadata attribute. For example, to group by an `os` metadata key, set `field: METADATA` and `key: os` on the column.

## Filtering and sorting

### `where`

Use `where` to filter rows before they are displayed. Each condition specifies a `field` and a `type`.

Available filter types:

`CONTAINS`, `ENDS_WITH`, `EQUAL_TO`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL_TO`, `IN`, `IS_FALSE`, `IS_NOT_NULL`, `IS_NULL`, `IS_TRUE`, `LESS_THAN`, `LESS_THAN_OR_EQUAL_TO`, `NOT_CONTAINS`, `NOT_EQUAL_TO`, `NOT_IN`, `OR`, `PREFIX`, `REGEX`, `STARTS_WITH`

Use `OR` to combine multiple conditions with OR logic. All other conditions at the same level are combined with AND.

### `orderBy`

Use `orderBy` to sort results. Each entry has a `column` (the column key from `columns`) and an optional `order` (`ASC` or `DESC`, default `ASC`).

## Markdown chart

The `Markdown` chart does not use a `data` block. Instead, use the `source` property to define the content:

- `type: Text` — render static Markdown. Set `content` with the Markdown string.
- `type: FlowDescription` — pull the description from a flow. Set `flowId` and `namespace`.

## Example

A dashboard with a time series, bar chart, KPI, table, and a Markdown panel:

```yaml
id: getting_started
title: Getting Started
description: Example dashboard
timeWindow:
  default: P7D
  max: P365D
charts:
  - id: executions_timeseries
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Executions over time
      legend:
        enabled: true
      column: date
      colorByColumn: state
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        date:
          field: START_DATE
          displayName: Date
        state:
          field: STATE
        total:
          displayName: Executions
          agg: COUNT
          graphStyle: BARS
        duration:
          displayName: Duration
          field: DURATION
          agg: SUM
          graphStyle: LINES

  - id: executions_per_namespace
    type: io.kestra.plugin.core.dashboard.chart.Bar
    chartOptions:
      displayName: Executions per namespace
      width: 6
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        namespace:
          field: NAMESPACE
        state:
          field: STATE
        total:
          displayName: Executions
          agg: COUNT

  - id: success_ratio
    type: io.kestra.plugin.core.dashboard.chart.KPI
    chartOptions:
      displayName: Success ratio
      numberType: PERCENTAGE
      width: 3
    data:
      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
      columns:
        field: ID
        agg: COUNT
      numerator:
        - field: STATE
          type: IN
          values:
            - SUCCESS

  - id: table_metrics
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Sum of sales per namespace
      width: 6
    data:
      type: io.kestra.plugin.core.dashboard.data.Metrics
      columns:
        namespace:
          field: NAMESPACE
        value:
          field: VALUE
          agg: SUM
      where:
        - field: NAME
          type: EQUAL_TO
          value: sales_count
        - field: NAMESPACE
          type: IN
          values:
            - dev_graph
            - prod_graph
      orderBy:
        - column: value
          order: DESC

  - id: table_logs
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Log count by level
      width: 6
    data:
      type: io.kestra.plugin.core.dashboard.data.Logs
      columns:
        level:
          field: LEVEL
        count:
          agg: COUNT
      where:
        - field: NAMESPACE
          type: IN
          values:
            - dev_graph
            - prod_graph

  - id: dashboard_notes
    type: io.kestra.plugin.core.dashboard.chart.Markdown
    chartOptions:
      displayName: About this dashboard
      width: 12
    source:
      type: Text
      content: |
        ## Interpretation guide
        - The **success ratio** KPI tracks executions in a `SUCCESS` state over the selected time window.
        - Filter the dashboard by namespace using the global filter controls at the top of the page.
```

For more examples, check the [GitHub repository](https://github.com/kestra-io/enterprise-edition-examples) and explore Dashboard Blueprints.
