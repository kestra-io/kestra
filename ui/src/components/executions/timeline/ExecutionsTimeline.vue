<template>
    <div class="executions-timeline" :class="{expanded}">
        <TimelineToolbar
            :rangeStartMs="rangeStartMs"
            :rangeEndMs="rangeEndMs"
            :activePreset="activePreset"
            :expanded="expanded"
            @apply-preset="applyPreset"
            @custom-range="({startMs, endMs}) => setRange(startMs, endMs)"
            @zoom="zoom"
            @pan="pan"
            @now="goToNow"
            @update:expanded="expanded = $event"
        />

        <div v-if="showBreadcrumb" class="timeline-breadcrumb-bar">
            <KsBreadcrumb :items="breadcrumbItems" />
            <div class="scope-stats">
                <KsTag>{{ $t("executionsTimeline.breadcrumb.total", {count: scopedExecutions.length}) }}</KsTag>
                <KsTag v-if="scopedFailedCount > 0" type="danger">
                    {{ $t("executionsTimeline.breadcrumb.failed", {count: scopedFailedCount}) }}
                </KsTag>
            </div>
        </div>

        <div ref="bodyRef" class="timeline-body">
            <KsSkeleton :loading="loading" :rows="5" animated>
                <template #template>
                    <div class="timeline-skeleton">
                        <div v-for="i in 5" :key="i" class="skel-row">
                            <KsSkeleton :rows="1" animated style="width: 11.25rem" />
                            <KsSkeleton :rows="1" animated style="flex: 1" />
                        </div>
                    </div>
                </template>

                <KsAlert v-if="error" type="error" :title="$t('executionsTimeline.error.title')" :description="error" :closable="false" />

                <KsEmpty v-else-if="rows.length === 0">
                    <template #description>
                        {{ $t("executionsTimeline.empty.description") }}
                    </template>
                    <div class="empty-actions">
                        <KsButton @click="resetFilters">
                            {{ $t("executionsTimeline.empty.resetFilters") }}
                        </KsButton>
                        <KsButton type="primary" @click="applyPreset('PT168H')">
                            {{ $t("executionsTimeline.empty.widenRange") }}
                        </KsButton>
                    </div>
                </KsEmpty>

                <template v-else>
                    <TimelineRow
                        v-for="row in rows"
                        :key="row.key"
                        :label="row.label"
                        :executions="row.executions"
                        :total="row.total"
                        :failed="row.failed"
                        :rangeStartMs="rangeStartMs"
                        :rangeEndMs="rangeEndMs"
                        :availableWidthPx="availableWidthPx"
                        :packLanes="viewLevel === 'flow'"
                        :dimmedStates="dimmedStates"
                        @drill-in="onDrillIn(row)"
                        @open-own-page="onOpenOwnPage(row)"
                        @show-only-flow="onShowOnlyFlow"
                    />
                </template>
            </KsSkeleton>
        </div>

        <ChartLegend
            v-if="legendItems.length > 0"
            :items="legendItems"
            :formatValue="(v: number) => String(v)"
            @toggle="onLegendToggle"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, onUnmounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import type {FilterConfiguration, KsBreadcrumbItem} from "@kestra-io/design-system"

    import TimelineToolbar from "./TimelineToolbar.vue"
    import TimelineRow from "./TimelineRow.vue"
    import ChartLegend from "../../dashboard/sections/ChartLegend.vue"
    import {useExecutionsStore} from "../../../stores/executions"
    import {useExecutionsQueryScope} from "../../../composables/useExecutionsQueryScope"
    import {useTimelineRange} from "../../../composables/useTimelineRange"
    import {useExecutionFilter, useFlowExecutionFilter} from "../../filter/configurations"
    import {groupByNamespace, countByState, type TimelineExecution} from "../../../utils/executionsTimeline"

    const MAX_FETCHED_EXECUTIONS = 1000

    const props = withDefaults(defineProps<{
        namespace?: string;
        flowId?: string;
        statuses?: string[];
    }>(), {
        namespace: undefined,
        flowId: undefined,
        statuses: () => [],
    })

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()

    const executionFilter = useExecutionFilter()
    const flowExecutionFilter = useFlowExecutionFilter()

    const drillNamespace = ref<string | undefined>(undefined)
    const drillFlowId = ref<string | undefined>(undefined)

    const effectiveNamespace = computed(() => props.namespace ?? drillNamespace.value)
    const effectiveFlowId = computed(() => props.flowId ?? drillFlowId.value)

    const viewLevel = computed<"all" | "namespace" | "flow">(() => {
        if (effectiveFlowId.value) return "flow"
        if (effectiveNamespace.value) return "namespace"
        return "all"
    })

    const activeFilterConfiguration = computed<FilterConfiguration>(() =>
        (effectiveNamespace.value === undefined || effectiveFlowId.value === undefined) ? executionFilter.value : flowExecutionFilter.value,
    )
    const {loadQuery} = useExecutionsQueryScope(activeFilterConfiguration, computed(() => ({
        namespace: effectiveNamespace.value,
        flowId: effectiveFlowId.value,
        statuses: props.statuses,
    })))

    const {rangeStartMs, rangeEndMs, activePreset, setRange, applyPreset, zoom, pan, goToNow} = useTimelineRange()

    const expanded = ref(false)
    const loading = ref(false)
    const error = ref<string | undefined>(undefined)
    const rawExecutions = ref<TimelineExecution[]>([])
    const dimmedStates = ref(new Set<string>())
    const bodyRef = ref<HTMLElement | null>(null)
    const availableWidthPx = ref(600)

    const showBreadcrumb = computed(() => props.flowId === undefined)

    const scopedExecutions = computed(() => rawExecutions.value)
    const scopedFailedCount = computed(() =>
        scopedExecutions.value.filter(e => ["FAILED", "KILLED", "WARNING"].includes(e.state)).length,
    )

    interface TimelineRowData {
        key: string;
        label: string;
        executions: TimelineExecution[];
        total: number;
        failed: number;
        namespace: string;
        flowId?: string;
    }

    const rows = computed<TimelineRowData[]>(() => {
        if (viewLevel.value === "flow") {
            return [{
                key: effectiveFlowId.value!,
                label: effectiveFlowId.value!,
                executions: scopedExecutions.value,
                total: scopedExecutions.value.length,
                failed: scopedFailedCount.value,
                namespace: effectiveNamespace.value!,
                flowId: effectiveFlowId.value,
            }]
        }

        if (viewLevel.value === "namespace") {
            const group = groupByNamespace(scopedExecutions.value).find(g => g.namespace === effectiveNamespace.value)
            return (group?.flows ?? []).map(flow => ({
                key: `${flow.namespace}.${flow.flowId}`,
                label: flow.flowId,
                executions: flow.executions,
                total: flow.total,
                failed: flow.failed,
                namespace: flow.namespace,
                flowId: flow.flowId,
            }))
        }

        return groupByNamespace(scopedExecutions.value).map(group => ({
            key: group.namespace,
            label: group.namespace,
            executions: group.flows.flatMap(f => f.executions),
            total: group.total,
            failed: group.failed,
            namespace: group.namespace,
        }))
    })

    const legendItems = computed(() =>
        countByState(scopedExecutions.value).map(({state, count}) => ({
            label: state,
            color: `var(--ks-chart-${state.toLowerCase()})`,
            count,
        })),
    )

    const breadcrumbItems = computed<KsBreadcrumbItem[]>(() => {
        const items: KsBreadcrumbItem[] = []
        if (props.namespace === undefined) {
            items.push({
                label: t("executionsTimeline.breadcrumb.all"),
                onClick: resetToAll,
                disabled: viewLevel.value === "all",
            })
        }
        if (effectiveNamespace.value) {
            items.push({
                label: effectiveNamespace.value,
                onClick: resetToNamespace,
                disabled: viewLevel.value === "namespace" || props.namespace !== undefined,
            })
        }
        if (effectiveFlowId.value) {
            items.push({label: effectiveFlowId.value, disabled: true})
        }
        return items
    })

    function resetToAll() {
        drillNamespace.value = undefined
        drillFlowId.value = undefined
    }

    function resetToNamespace() {
        drillFlowId.value = undefined
    }

    function onDrillIn(row: TimelineRowData) {
        if (viewLevel.value === "flow") return
        if (viewLevel.value === "all") {
            drillNamespace.value = row.namespace
        } else if (row.flowId) {
            drillFlowId.value = row.flowId
        }
    }

    function onOpenOwnPage(row: TimelineRowData) {
        if (row.flowId) {
            router.push({name: "flows/update", params: {namespace: row.namespace, id: row.flowId}})
        } else {
            router.push({name: "namespaces/update", params: {id: row.namespace}})
        }
    }

    function onShowOnlyFlow({namespace, flowId}: {namespace: string; flowId: string}) {
        drillNamespace.value = namespace
        drillFlowId.value = flowId
    }

    function onLegendToggle(state: string) {
        const next = new Set(dimmedStates.value)
        if (next.has(state)) next.delete(state)
        else next.add(state)
        dimmedStates.value = next
    }

    function resetFilters() {
        const {page: _p, size: _s, sort: _so, ...rest} = route.query
        const cleared = Object.fromEntries(Object.entries(rest).filter(([key]) => !key.startsWith("filters[") && key !== "q"))
        router.push({query: cleared})
    }

    async function fetchExecutions() {
        loading.value = true
        error.value = undefined
        try {
            const response = await executionsStore.findExecutions(loadQuery({
                size: MAX_FETCHED_EXECUTIONS,
                page: 1,
                sort: "state.startDate:desc",
                commit: false,
            }))
            rawExecutions.value = (response.results ?? [])
                .filter((execution: {state?: {startDate?: string}}) => execution.state?.startDate)
                .map((execution: {id: string; namespace: string; flowId: string; state: {current: string; startDate: string; endDate?: string}}) => ({
                    id: execution.id,
                    namespace: execution.namespace,
                    flowId: execution.flowId,
                    state: execution.state.current,
                    startMs: new Date(execution.state.startDate).getTime(),
                    endMs: execution.state.endDate ? new Date(execution.state.endDate).getTime() : Date.now(),
                }))
        } catch {
            error.value = t("executionsTimeline.error.description")
        } finally {
            loading.value = false
        }
    }

    const fetchKey = computed(() => JSON.stringify({
        query: route.query,
        namespace: effectiveNamespace.value,
        flowId: effectiveFlowId.value,
        rangeStartMs: rangeStartMs.value,
        rangeEndMs: rangeEndMs.value,
    }))

    watch(fetchKey, fetchExecutions, {immediate: true})

    let resizeObserver: ResizeObserver | undefined

    onMounted(() => {
        if (bodyRef.value && typeof ResizeObserver !== "undefined") {
            resizeObserver = new ResizeObserver(entries => {
                const entry = entries[0]
                if (entry) availableWidthPx.value = Math.max(entry.contentRect.width - 180, 100)
            })
            resizeObserver.observe(bodyRef.value)
        }
    })

    onUnmounted(() => {
        resizeObserver?.disconnect()
    })
</script>

<style scoped lang="scss">
.executions-timeline {
    display: flex;
    flex-direction: column;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
    box-shadow: 0 var(--ks-spacing-1) var(--ks-spacing-2) 0 var(--ks-shadow-element);
    overflow: hidden;
    margin-bottom: var(--ks-spacing-4);
}

.timeline-breadcrumb-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
}

.scope-stats {
    display: flex;
    gap: var(--ks-spacing-2);
}

.timeline-body {
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    max-height: 18.75rem;
    overflow-y: auto;
}

.executions-timeline.expanded .timeline-body {
    max-height: none;
}

.timeline-skeleton {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
}

.skel-row {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
}

.empty-actions {
    display: flex;
    justify-content: center;
    gap: var(--ks-spacing-2);
    margin-top: var(--ks-spacing-2);
}
</style>
