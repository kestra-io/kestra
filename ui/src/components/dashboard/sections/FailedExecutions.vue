<template>
    <section v-if="data?.results?.length" id="failed-executions">
        <KsDataTable
            :id="containerID"
            :data="data.results"
            :total="isPaginationEnabled(props.chart) ? data.total : 0"
            :currentPage="pageNumber"
            :pageSize="pageSize"
            :height="320"
            size="small"
            @page-changed="handlePageChange"
        >
            <KsTableColumn type="expand">
                <template #default="scope">
                    <ExpandedErrorLogs :executionId="getExecutionId(scope.row)" />
                </template>
            </KsTableColumn>

            <KsTableColumn
                v-for="[key, value] in Object.entries(props.chart.data?.columns ?? {})"
                :label="value.displayName || key"
                :key
                :width="value.field === 'STATE' ? 140 : undefined"
            >
                <template #default="scope">
                    <template v-if="resolvedComponent(value.field) === undefined">
                        {{ scope.row[key] }}
                    </template>
                    <component
                        v-else
                        :is="resolvedComponent(value.field)"
                        v-bind="resolvedProps(value.field, key, scope.row)"
                    />
                </template>
            </KsTableColumn>
        </KsDataTable>
    </section>

    <KsEmpty v-else :description="$t('dashboards.flow_overview.recent_failures_empty')" />
</template>

<script setup lang="ts">
    import {PropType, ref, watch} from "vue";
    import {useRoute} from "vue-router";

    import type {Chart} from "../types.ts";
    import {isPaginationEnabled, useChartGenerator} from "../composables/useDashboards";
    import {useTableRenderer} from "./table/useTableRenderer";
    import {FilterObject} from "../../../utils/filters";

    import ExpandedErrorLogs from "./failedExecutions/ExpandedErrorLogs.vue";

    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const containerID = `${props.chart.id}__${Math.random()}`;
    const route = useRoute();

    const {resolvedComponent, resolvedProps} = useTableRenderer({
        chartColumns: () => props.chart.data?.columns,
        routeNamespace: () =>
            typeof route.params.namespace === "string" ? route.params.namespace : undefined,
    });

    const {generate} = useChartGenerator(props.dashboardId, props, false);

    const data = ref();
    const pageNumber = ref(1);
    const pageSize = ref(10);

    const getData = async () => (data.value = await generate(
        isPaginationEnabled(props.chart) ? {pageNumber: pageNumber.value, pageSize: pageSize.value} : undefined,
    ));

    const handlePageChange = (options: { page?: number; size?: number | string }) => {
        if (pageNumber.value === options.page && pageSize.value === options.size) return;

        pageNumber.value = options.page ?? 1;
        const sizeNumber = typeof options.size === "string" ? parseInt(options.size, 10) : options.size;
        pageSize.value = sizeNumber && !isNaN(sizeNumber) ? sizeNumber : 10;

        return getData();
    };

    function refresh() {
        return getData();
    }

    defineExpose({refresh});

    watch(() => route.params.filters, () => refresh(), {deep: true, immediate: true});

    const getExecutionId = (row: Record<string, any>): string | undefined => {
        const idColumnEntry = Object.entries(props.chart.data?.columns ?? {})
            .find(([, descriptor]) => descriptor.field === "ID");
        const idKey = idColumnEntry?.[0] ?? "id";
        return typeof row[idKey] === "string" ? row[idKey] : undefined;
    };
</script>

<style lang="scss" scoped>
    section#failed-executions {
        height: 100%;
    }
</style>
