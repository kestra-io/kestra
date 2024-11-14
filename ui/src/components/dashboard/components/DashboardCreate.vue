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
  - id: time_series
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Executions per country over time
      description: Count executions per country label and execution state # optional icon on hover
      tooltip: ALL # ALL, NONE, SINGLE
      legend:
        enabled: true # later on possible to extend it e.g. position AUTO, LEFT, RIGHT, TOP, BOTTOM
      # colorScheme: CLASSIC # PURPLE - TBD - we may sync with the Settings color scheme as in the main dashboard
      column: executionDate
      colorByColumn: state
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions # also: Logs and Metrics available
      columns:
        executionDate:
          field: START_DATE
          displayName: Execution Date
        country:
          field: LABELS
          labelKey: country
          displayName: Country
          # alternative definition timeseries: true
        state:
          field: STATE
          displayName: Execution State
        total: # left vertical axis
          field: ID
          displayName: Total Executions
          agg: COUNT
          graphStyle: BARS # LINES, BARS, POINTS
        duration: # left vertical axis
          field: DURATION
          displayName: Total Executions
          agg: SUM
          graphStyle: LINES # LINES, BARS, POINTS
      where:
        - field: NAMESPACE
          type: IN
          values:
            - dev
            - prod
      orderBy:
        total: DESC
        duration: ASC

  - id: markdown_section
    type: io.kestra.plugin.core.dashboard.chart.Markdown
    chartOptions:
      displayName: Executions per country over time
      description: Count executions per country label and execution state # optional icon on hover
    content: |
      ## This is a markdown panel

  - id: executions_per_country
    type: io.kestra.plugin.core.dashboard.chart.Bar
    chartOptions:
      displayName: Executions per country
      description: Count executions per country label and execution state # optional icon on hover
      tooltip: ALL # ALL, NONE, SINGLE
      legend:
        enabled: true # later on possible to extend it e.g. position AUTO, LEFT, RIGHT, TOP, BOTTOM
      # colorScheme: CLASSIC # PURPLE - TBD - we may sync with the Settings color scheme as in the main dashboard
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        namespace:
          field: NAMESPACE
          displayName: Namespace
          limit: 100
        state:
          field: STATE
          displayName: Execution State
          limit: 10
        total: # left vertical axis
          field: ID
          displayName: Total Executions
          agg: COUNT
      orderBy:
        total: DESC

  - id: total_executions_per_country
    type: io.kestra.plugin.core.dashboard.chart.Pie
    chartOptions:
      displayName: Executions per country
      description: Count executions per country label and execution state
      graphStyle: PIE # PIE, DONUT - donutdefault
      legend:
        enabled: true # later on possible to extend it e.g. position AUTO, LEFT, RIGHT, TOP, BOTTOM
      # colorScheme: CLASSIC # PURPLE - TBD - we may sync with the Settings color scheme as in the main dashboard
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        country:
          field: LABELS
          labelKey: country
          displayName: Country
        total:
          field: ID
          agg: COUNT
          displayName: Total Executions

  - id: table
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Executions per country
      description: Count executions per country label and execution state
      header:
        enabled: true # header = column names; in the future can add customization
      pagination:
        enabled: true # in the future: possible to add page size
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        id:
          field: ID
        country:
          field: LABELS
          labelKey: country
        env:
          field: LABELS
          labelKey: env
        state:
          field: STATE
          displayName: Execution State
        duration:
          field: DURATION
      where:
        - field: NAMESPACE
          type: IN
          values:
            - prod
            - dev
        - type: OR
          values:
            - field: STATE
              type: EQUAL_TO
              value: PAUSED
            - field: LABELS
              labelKey: country
              type: EQUAL_TO
              value: FR
      orderBy:
        duration: DESC
# possible WHERE filters are EQUAL_TO, NOT_EQUAL_TO, GREATER_THAN, LESS_THAN, BETWEEN, GREATER_THAN_OR_EQUAL_TO, LESS_THAN_OR_EQUAL_TO, IS_EMPTY, NOT_EMPTY
#    layout:
#      width: 24 # int nr max 24
#      height: 8 # int nr max 24`
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
