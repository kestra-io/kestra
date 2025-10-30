<template>
    <MetricsTable
        v-if="executionsStore.execution"
        ref="table"
        :taskRunId="route.query.metric?.[0] ?? undefined"
        :showTask="true"
        :execution="executionsStore.execution"
        :optionalColumns="optionalColumns"
    >
        <template #navbar>
            <KSFilter
                :configuration="metricFilter"
                :properties="{
                    shown: true,
                    columns: optionalColumns,
                    storageKey: 'execution-metrics'
                }"
                :prefix="'execution-metrics'"
                :tableOptions="{
                    chart: {shown: false},
                    refresh: {shown: true, callback: refresh}
                }"
                @update-properties="updateDisplayColumns"
                legacyQuery
            />
        </template>
    </MetricsTable>
</template>
<script setup lang="ts">
    import {onMounted, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {useExecutionsStore} from "../../stores/executions";
    import {useMetricFilter} from "../filter/configurations";
    import MetricsTable from "../executions/MetricsTable.vue";
    import KSFilter from "../filter/components/KSFilter.vue";

    const {t} = useI18n();
    const route = useRoute();
    const executionsStore = useExecutionsStore();
    
    const metricFilter = useMetricFilter();

    const table = ref<typeof MetricsTable>();

    const optionalColumns = ref([
        {
            label: t("task"), 
            prop: "taskId", 
            default: true, 
            description: t("filter.table_column.metrics.task")
        },
        {
            label: t("name"), 
            prop: "name", 
            default: true, 
            description: t("filter.table_column.metrics.name")
        },
        {
            label: t("value"), 
            prop: "value", 
            default: true, 
            description: t("filter.table_column.metrics.value")
        },
        {
            label: t("tags"), 
            prop: "tags", 
            default: true, 
            description: t("filter.table_column.metrics.tags")
        },
    ]);

    const updateDisplayColumns = (newColumns: string[]) => {
        table.value?.updateDisplayColumns(newColumns);
    };

    const refresh = () => {
        table.value!.loadData(table.value!.onDataLoaded);
    };

    onMounted(() => {
        table.value!.loadData(table.value!.onDataLoaded);
    });
</script>