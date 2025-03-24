# Custom Dashboards as Code

Build custom dashboards to track workflow `executions`, `logs` and `metrics` filtered by specific namespaces or labels and showing data you care about.

The declarative syntax allows you to manage dashboards as code — you can version control that dashboard definition alongside your flows.

## Example

Below is an example dashboard definition that displays:
- **time series** of **executions** over time
- **table** with specific **metrics** aggregated per namespace
- **table** with **logs** aggregated per log level and namespace.

```yaml
title: Getting Started
description: First custom dashboard
timeWindow:
  default: P7D
  max: P365D
charts:
  - id: executions_timeseries
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Executions
      description: Executions last week
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

  - id: table_metrics
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Sum of sales per namespace
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
      displayName: Log count by level for filtered namespace
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
```

For more examples, check our [GitHub repository](https://github.com/kestra-io/enterprise-edition-examples) and explore Dashboard Blueprints.

## Querying data

The `data` property of a chart defines the type of data that is queried. The `type` determines which columns are displayed.

Dashboards can query data from these source `types`:
- `type: io.kestra.plugin.core.dashboard.data.Executions`: data related to your workflow executions
- `type: io.kestra.plugin.core.dashboard.data.Logs`: logs produced by your executions
- `type: io.kestra.plugin.core.dashboard.data.Metrics`: metrics emitted during executions.

After defining the data source, specify the **columns** to display in the chart. Each column is defined by the `field` and may include additional optional properties.

| Property | Description                                                                                                    |
| --- |----------------------------------------------------------------------------------------------------------------|
| `field` | The only required field, specifies the name of the column in the data source to use                           |
| `displayName` | Sets the label displayed in the chart                                                                  |
| `agg` |  Defines the aggregation function applied to the column: supported aggregations include `AVG`, `COUNT`, `MAX`, `MIN`, `SUM` |
| `graphStyle` | Indicates the style of the graph displayed: supported styles include `LINES`, `BARS`, `POINTS`                          |
| `columnAlignment` | Specifies the alignment of the column in the table: supported alignments include `LEFT`, `RIGHT`, `CENTER`               |


You can also use the `where` property to set conditions that filter the result set before displaying it in the chart. Filters can apply to any column in the data source. For shared logic, use the `AND` operator in the `where` property to combine several conditions. If multiple conditions are needed with a different logic, use the `type: OR` property.

Available filter types include:
- `CONTAINS`
- `ENDS_WITH`
- `EQUAL_TO`
- `GREATER_THAN`
- `GREATER_THAN_OR_EQUAL_TO`
- `IN`
- `IS_FALSE`
- `IS_NOT_NULL`
- `IS_NULL`
- `IS_TRUE`
- `LESS_THAN`
- `LESS_THAN_OR_EQUAL_TO`
- `NOT_EQUAL_TO`
- `NOT_IN`
- `OR`
- `REGEX`
- `STARTS_WITH`.

Available field types include the following columns:
- `ATTEMPT_NUMBER`
- `DATE`
- `DURATION`
- `END_DATE`
- `EXECUTION_ID`
- `FLOW_ID`
- `FLOW_REVISION`
- `ID`
- `LABELS`
- `LEVEL`
- `MESSAGE`
- `NAME`
- `NAMESPACE`
- `START_DATE`
- `STATE`
- `TASK_ID`
- `TASK_RUN_ID`
- `TRIGGER_ID`
- `TYPE`
- `VALUE`.

Note that some of the above are reserved only for specific types of `data` e.g., the `LEVEL` column is only available for `type: io.kestra.plugin.core.dashboard.data.Logs`.
