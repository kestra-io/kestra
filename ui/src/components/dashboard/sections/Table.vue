<template>
    <section v-if="data" id="table">
        <el-table
            :id="containerID"
            :data="data.results"
            :height="240"
            size="small"
        >
            <el-table-column
                v-for="[key, value] in Object.entries( props.chart.data?.columns ?? {} )"
                :label="value.displayName || key"
                :key
                :width="value.field === 'STATE' ? 140 : null"
            >
                <template #default="scope">
                    <component :is="resolvedComponent(value.field)" v-bind="resolvedProps(value.field, key, scope.row)">
                        <template v-if="!resolvedComponent(value.field)">
                            {{ scope.row[key] }}
                        </template>
                    </component>
                </template>
            </el-table-column>
        </el-table>

        <Pagination
            v-if="isPaginationEnabled(props.chart)"
            :total="data.total"
            :page="pageNumber"
            :size="pageSize"
            @page-changed="handlePageChange"
        />
    </section>

    <NoData v-else :text="EMPTY_TEXT" />
</template>

<script setup lang="ts">
    import {PropType, watch, ref, computed} from "vue";

    import type {RouteLocation} from "vue-router";

    import type {Chart} from "../composables/useDashboards";
    import {getDashboard, isPaginationEnabled, useChartGenerator} from "../composables/useDashboards";

    import Date from "./table/columns/Date.vue";
    import Duration from "./table/columns/Duration.vue";
    import Link from "./table/columns/Link.vue";
    import Namespace from "./table/columns/Namespace.vue";
    import Status from "../../Status.vue";

    import Pagination from "../../layout/Pagination.vue";
    import NoData from "../../layout/NoData.vue";

    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });

    const containerID = `${props.chart.id}__${Math.random()}`;

    const resolvedComponent = (field: string) => {
        switch (field) {
        case "ID":
        case "FLOW_ID":
            return Link;
        case "NAMESPACE":
            return Namespace;
        case "STATE":
            return Status;
        case "DURATION":
            return Duration;
        default:
            if (field.toLowerCase().includes("date")) return Date;
            return undefined;
        }
    };

    const resolvedProps = (field: string, key: string, row: Record<string, any>) => {
        const baseProps = {field: key, row, columns: props.chart.data?.columns ?? {}};

        switch (field) {
        case "ID":
            return {...baseProps, execution: true};
        case "FLOW_ID":
            return {...baseProps, flow: true};
        case "NAMESPACE":
            return {field: row[key]};
        case "STATE":
            return {size: "small", status: row[key]};
        case "DURATION":
            return {field: row[key]};
        default:
            if (field.toLowerCase().includes("date")) {
                return {field: row[key]};
            }
            return {};
        }
    };

    const data = ref();
    const {EMPTY_TEXT, generate} = useChartGenerator(props, false);

    import {useRoute} from "vue-router";
    import {FilterObject} from "../../../utils/filters";
    const route = useRoute();

    const getData = async (ID: string) => (data.value = await generate(ID, pagination.value));

    const pageNumber = ref(1);
    const pageSize = ref(25);

    const pagination = computed(() => {
        return isPaginationEnabled(props.chart)
            ? {pageNumber: pageNumber.value, pageSize: pageSize.value}
            : undefined;
    });

    const dashboardID = (route: RouteLocation) => getDashboard(route, "id") as string;

    const handlePageChange = (options: { page: number; size: number }) => {
        if (pageNumber.value === options.page && pageSize.value === options.size) return;

        pageNumber.value = options.page;
        pageSize.value = options.size;

        return getData(dashboardID(route));
    };

    function refresh() {
        return getData(dashboardID(route));
    }

    defineExpose({
        refresh
    });

    watch(() => route.params.filters, () => {
        refresh();
    }, {deep: true, immediate: true});
</script>

<style scoped>
/* Improve scrollbar contrast for better visibility in dark theme */
:deep(.el-table__body-wrapper) {
    /* Horizontal scrollbar styling */
    scrollbar-width: thin;
    scrollbar-color: #6B7280 var(--ks-background-body);
}

:deep(.el-table__body-wrapper)::-webkit-scrollbar {
    height: 8px;
}

:deep(.el-table__body-wrapper)::-webkit-scrollbar-track {
    background: var(--ks-background-body);
    border-radius: 4px;
}

:deep(.el-table__body-wrapper)::-webkit-scrollbar-thumb {
    background: #6B7280;
    border-radius: 4px;
    border: 1px solid var(--ks-background-body);
}

:deep(.el-table__body-wrapper)::-webkit-scrollbar-thumb:hover {
    background: #9CA3AF;
}

/* Dark theme specific improvements */
html.dark :deep(.el-table__body-wrapper) {
    scrollbar-color: #9CA3AF var(--ks-background-body);
}

html.dark :deep(.el-table__body-wrapper)::-webkit-scrollbar-thumb {
    background: #9CA3AF;
    border: 1px solid var(--ks-background-body);
}

html.dark :deep(.el-table__body-wrapper)::-webkit-scrollbar-thumb:hover {
    background: #D1D5DB;
}
</style>
