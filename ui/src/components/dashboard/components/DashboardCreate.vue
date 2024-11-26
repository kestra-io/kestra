<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <dashboard-editor @save="save" :initial-source="initialSource" />
    </section>
</template>

<script>
    import RouteContext from "../../../mixins/routeContext";
    import TopNavBar from "../../../components/layout/TopNavBar.vue";
    import DashboardEditor from "./DashboardEditor.vue";

    export default {
        mixins: [RouteContext],
        components: {
            DashboardEditor,
            TopNavBar
        },
        data() {
            return {
                initialSource: `title: Executions per country
description: Count executions per country label and execution state
timeWindow:
  default: P30D # P30DT30H
  max: P365D

charts:
  - id: timeseries_executions
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Executions per country over time
      description: Count executions per country label and execution state # optional icon on hover
      tooltip: ALL # ALL, NONE, SINGLE
      legend:
        enabled: true # later on possible to extend it e.g. position AUTO, LEFT, RIGHT, TOP, BOTTOM
      # colorScheme: CLASSIC # PURPLE - TBD - we may sync with the Settings color scheme as in the main dashboard
      column: date
      colorByColumn: state
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions # also: Logs and Metrics available
      columns:
        date:
          field: START_DATE
          displayName: Execution Date
        country:
          field: LABELS
          labelKey: country
        state:
          field: STATE
        duration: # left vertical axis
          displayName: Executions duration
          field: DURATION
          agg: SUM
          graphStyle: LINES # LINES, BARS, POINTS
        total: # left vertical axis
          displayName: Total Executions
          agg: COUNT
          graphStyle: BARS # LINES, BARS, POINTS
      where:
        - field: NAMESPACE
          type: IN
          values:
            - dev_graph
            - prod_graph

  - id: timeseries_executions_ns
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Executions per country per namespace over time
      description: Count executions per country label, execution state and namespace # optional icon on hover
      tooltip: ALL # ALL, NONE, SINGLE
      legend:
        enabled: true # later on possible to extend it e.g. position AUTO, LEFT, RIGHT, TOP, BOTTOM
      # colorScheme: CLASSIC # PURPLE - TBD - we may sync with the Settings color scheme as in the main dashboard
      column: date
      colorByColumn: state
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions # also: Logs and Metrics available
      columns:
        namespace:
          field: NAMESPACE
        date:
          field: START_DATE
          displayName: Execution Date
        country:
          field: LABELS
          labelKey: country
        state:
          field: STATE
        duration: # left vertical axis
          displayName: Executions duration
          field: DURATION
          agg: SUM
          graphStyle: LINES # LINES, BARS, POINTS
        total: # left vertical axis
          displayName: Total Executions
          agg: COUNT
          graphStyle: BARS # LINES, BARS, POINTS
      where:
        - field: NAMESPACE
          type: IN
          values:
            - dev_graph
            - prod_graph
  - id: table_logs
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Log count by level for filtered namespace
    data:
      type: io.kestra.plugin.core.dashboard.data.Logs # also: Logs and Metrics available
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
  - id: table_executions
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Executions per country, state and date
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions # also: Logs and Metrics available
      columns:
        date:
          field: START_DATE
          displayName: Execution Date
        country:
          field: LABELS
          labelKey: country
        state:
          field: STATE
        duration: # left vertical axis
          displayName: Executions duration
          field: DURATION
          agg: SUM
        total: # left vertical axis
          displayName: Total Executions
          agg: COUNT
      where:
        - field: NAMESPACE
          type: IN
          values:
            - dev_graph
            - prod_graph
      orderBy:
        - column: date
          order: ASC
        - column: duration
          order: DESC

  - id: table_metrics
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Sum of sales per namespace
    data:
      type: io.kestra.plugin.core.dashboard.data.Metrics # also: Logs and Metrics available
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
          order: DESC`
            }
        },
        methods: {
            async save(input) {
                const dashboard = await this.$store.dispatch("dashboard/create", input);
                this.$store.dispatch("core/isUnsaved", false);
                this.$router.push({name: "dashboards/update", params: {id: dashboard.id}});
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("dashboards")
                };
            }
        }
    };
</script>
