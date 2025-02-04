<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <dashboard-editor v-if="initialSource" allow-save-unchanged @save="save" :initial-source="initialSource" />
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
                initialSource: undefined
            }
        },
        async beforeMount() {
            const blueprintId = this.$route.query.blueprintId;
            if (blueprintId !== undefined) {
                this.initialSource = await this.$store.dispatch("blueprints/getBlueprintSource", {type: "community", kind: "dashboard", id: blueprintId});
            } else {
                this.initialSource = `title: Overview
description: Default overview dashboard
timeWindow:
  default: P30D # P30DT30H
  max: P365D

charts:
  - id: executions_timeseries
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Executions
      description: Executions duration and count per date
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

  - id: executions_pie
    type: io.kestra.plugin.core.dashboard.chart.Pie
    chartOptions:
      displayName: Total Executions
      description: Total executions per state
      legend:
        enabled: true
      colorByColumn: state
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        state:
          field: STATE
        total:
          agg: COUNT

  - id: executions_in_progress
    type: io.kestra.plugin.core.dashboard.chart.Table
    chartOptions:
      displayName: Executions In Progress
      description: In-Progress executions data
    data:
      type: io.kestra.plugin.core.dashboard.data.Executions
      columns:
        id:
          field: ID
        namespace:
          field: NAMESPACE
        flowId:
          field: FLOW_ID
        duration:
          field: DURATION
        state:
          field: STATE
      where:
        - field: STATE
          type: IN
          values:
            - RUNNING
            - PAUSED
            - RESTARTED
            - KILLING
            - QUEUED
            - RETRYING
      orderBy:
        - column: duration
          order: DESC

  - id: executions_per_namespace_bars
    type: io.kestra.plugin.core.dashboard.chart.Bar
    chartOptions:
      displayName: Executions (per namespace)
      description: Executions count per namespace
      legend:
        enabled: true
      column: namespace
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

  - id: logs_timeseries
    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
    chartOptions:
      displayName: Logs
      description: Logs count per date grouped by level
      legend:
        enabled: true
      column: date
      colorByColumn: level
    data:
      type: io.kestra.plugin.core.dashboard.data.Logs
      columns:
        date:
          field: DATE
          displayName: Execution Date
        level:
          field: LEVEL
        total:
          displayName: Total Executions
          agg: COUNT
          graphStyle: BARS`;
            }
        },
        methods: {
            async save(input) {
                const dashboard = await this.$store.dispatch("dashboard/create", input);

                this.$toast().saved(dashboard.title);

                this.$store.dispatch("core/isUnsaved", false);
                this.$router.push({name: "home", params: {id: dashboard.id}});
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
