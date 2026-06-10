<template>
    <div>
        <div class="quick-filters">
            <template v-if="hasQuickFilters">
                <button
                    v-for="tab in QUICK_FILTER_TABS"
                    :key="tab.key"
                    class="quick-filter-tab"
                    :class="{active: activeTab === tab.key}"
                    :style="{'--tab-color': tabColor(tab)}"
                    @click="selectTab(tab.key)"
                >
                    {{ t(`dashboards.quick_filters.${tab.key}`) }}
                    <Motion
                        v-if="activeTab === tab.key"
                        as="span"
                        class="tab-indicator"
                        layoutId="tab-indicator"
                        :transition="{type: 'spring', stiffness: 400, damping: 30}"
                    />
                </button>
            </template>
        </div>

        <Motion
            as="div"
            :key="activeTab"
            :initial="{opacity: 0, y: 4}"
            :animate="{opacity: 1, y: 0}"
            :transition="{duration: 0.15, ease: 'easeOut'}"
        >
            <section v-if="data?.results?.length" id="table">
                <KsDataTable
                    :id="containerID"
                    :data="data.results"
                    :total="isPaginationEnabled(props.chart) ? data.total : 0"
                    :currentPage="pageNumber"
                    :pageSize="pageSize"
                    :height="240"
                    size="small"
                    noPaginationGutter
                    @page-changed="handlePageChange"
                >
                    <KsTableColumn
                        v-for="[key, value] in Object.entries( props.chart.data?.columns ?? {} )"
                        :label="value.displayName || key"
                        :key
                        :width="value.field === 'STATE' ? 140 : undefined"
                    >
                        <template #default="scope">
                            <template v-if="resolvedComponent(value.field) === undefined">
                                {{ scope.row[key] }}
                            </template>
                            <component v-else :is="resolvedComponent(value.field)" v-bind="resolvedProps(value.field, key, scope.row)" />
                        </template>
                    </KsTableColumn>
                </KsDataTable>
            </section>

            <KsEmpty v-else :description="EMPTY_TEXT" />
        </Motion>
    </div>
</template>

<script setup lang="ts">
    import {PropType, watch, ref, computed} from "vue"

    import type {Chart} from "../types.ts"
    import {isPaginationEnabled, useChartGenerator} from "../composables/useDashboards"

    import Date from "./table/columns/Date.vue"
    import Duration from "./table/columns/Duration.vue"
    import Link from "./table/columns/Link.vue"
    import Namespace from "./table/columns/Namespace.vue"
    import {KsExecutionStatus, cssVar} from "@kestra-io/design-system"
    import {Motion} from "motion-v"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n({useScope: "global"})

    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    })

    const containerID = `${props.chart.id}__${Math.random()}`

    const resolvedComponent = (field: string) => {
        switch (field) {
        case "ID":
        case "FLOW_ID":
            return Link
        case "NAMESPACE":
            return Namespace
        case "STATE":
            return KsExecutionStatus
        case "DURATION":
            return Duration
        default:
            if (field?.toLowerCase().includes("date")) return Date
            return undefined
        }
    }

    const resolvedProps = (field: string, key: string, row: Record<string, any>) => {
        const baseProps = {field: key, row, columns: props.chart.data?.columns ?? {}}

        switch (field) {
        case "ID":
            return {...baseProps, execution: true}
        case "FLOW_ID":
            return {...baseProps, flow: true}
        case "NAMESPACE":
            return {field: row[key]}
        case "STATE":
            return {
                size: "small",
                status: row[key].toString(),
            }
        case "DURATION":
            return {field: row[key], startDate: row["start_date"]}
        default:
            if (field.toLowerCase().includes("date")) {
                return {field: row[key], relative: field === "NEXT_EXECUTION_DATE"}
            }
            return {}
        }
    }

    const data = ref()

    import {useRoute} from "vue-router"
    import {FilterObject} from "../../../utils/filters"
    const route = useRoute()
    const {EMPTY_TEXT, generate} = useChartGenerator(props.dashboardId, props, false)

    const QUICK_FILTER_TABS = [
        {key: "all", states: [] as string[]},
        {key: "running", states: ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING", "KILLING"]},
        {key: "paused", states: ["PAUSED", "BREAKPOINT"]},
        {key: "success", states: ["SUCCESS"]},
        {key: "warning", states: ["WARNING"]},
        {key: "failed", states: ["FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED"]},
    ] as const

    type TabKey = (typeof QUICK_FILTER_TABS)[number]["key"]

    const EXECUTIONS_DATA_TYPE = "io.kestra.plugin.core.dashboard.data.Executions"
    const hasQuickFilters = computed(() => {
        if (props.chart.data?.type !== EXECUTIONS_DATA_TYPE) return false
        const columns = props.chart.data?.columns ?? {}
        return Object.values(columns).some((col: Record<string, any>) => col.field === "STATE")
    })

    const activeTab = ref<TabKey>("all")

    const TAB_STATUS_TOKEN: Record<string, string> = {
        running: "--ks-status-running",
        paused: "--ks-status-paused",
        success: "--ks-status-success",
        warning: "--ks-status-warning",
        failed: "--ks-status-error",
    }

    const tabColor = (tab: {key: string}) =>
        tab.key === "all"
            ? cssVar("--ks-text-link")
            : cssVar(TAB_STATUS_TOKEN[tab.key] ?? "")

    const activeStateFilter = computed((): FilterObject | null => {
        if (!hasQuickFilters.value || activeTab.value === "all") return null
        const tab = QUICK_FILTER_TABS.find(tab => tab.key === activeTab.value)
        if (!tab?.states.length) return null
        return {field: "state", operation: "IN", value: [...tab.states]}
    })

    const getData = async () => {
        const pagination = isPaginationEnabled(props.chart)
            ? {pageNumber: pageNumber.value, pageSize: pageSize.value}
            : undefined
        const append = activeStateFilter.value ? [activeStateFilter.value] : undefined
        data.value = await generate(pagination, undefined, append)
    }

    const selectTab = (key: TabKey) => {
        if (activeTab.value === key) return
        activeTab.value = key
        pageNumber.value = 1
        getData()
    }

    const pageNumber = ref(1)
    const pageSize = ref(25)

    const handlePageChange = (options: { page?: number; size?: number | string }) => {
        if (pageNumber.value === options.page && pageSize.value === options.size) return

        pageNumber.value = options.page ?? 1
        const sizeNumber = typeof options.size === "string" ? parseInt(options.size, 10) : options.size
        if (sizeNumber && isNaN(sizeNumber)) {
            pageSize.value = 25
            return
        };
        pageSize.value = sizeNumber ?? 25

        return getData()
    }

    function refresh() {
        return getData()
    }

    defineExpose({
        refresh,
    })

    watch(() => route.params.filters, () => {
        refresh()
    }, {deep: true, immediate: true})
</script>

<style lang="scss" scoped>
.quick-filters {
    display: flex;
    align-items: center;
    min-height: calc(var(--ks-spacing-2) * 2 + 1rem + 2px);
    border-bottom: 1px solid var(--ks-border-subtle);
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
        display: none;
    }
}

.quick-filter-tab {
    display: inline-flex;
    align-items: center;
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    font-size: 0.75rem;
    font-weight: 400;
    color: var(--ks-text-secondary);
    background: none;
    border: none;
    cursor: pointer;
    white-space: nowrap;
    position: relative;
    bottom: -1px;
    transition: color 0.15s, font-weight 0.1s;

    &:hover {
        color: var(--tab-color);
        font-weight: 500;
    }

    &.active {
        color: var(--tab-color);
        font-weight: 600;
    }
}

.tab-indicator {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: var(--tab-color);
    border-radius: 1px 1px 0 0;
}

section#table :deep(.kel-scrollbar__thumb) {
    background-color: var(--ks-btn-primary-bg-default) !important;
}
</style>
