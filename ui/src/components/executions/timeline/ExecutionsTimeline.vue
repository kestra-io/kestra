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

        <KsAlert
            v-if="isTruncated"
            type="warning"
            :description="$t('executionsTimeline.truncated.description', {shown: rawExecutions.length, total: fetchedTotal})"
            :closable="false"
        />

        <div ref="bodyRef" class="timeline-body">
            <KsSkeleton :loading="loading" :rows="5" animated>
                <template #template>
                    <div class="timeline-skeleton">
                        <div v-for="i in 5" :key="i" class="skel-row">
                            <KsSkeleton :rows="1" animated style="width: var(--timeline-row-label-width)" />
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
                    <div class="timeline-axis">
                        <span v-for="tick in axisTicks" :key="tick.key" class="timeline-axis-tick">{{ tick.label }}</span>
                        <span v-if="nowPercent !== undefined" class="timeline-axis-now" :style="{left: `${nowPercent}%`}">
                            {{ $t("now") }}
                        </span>
                    </div>

                    <TransitionGroup name="timeline-row" tag="div" class="timeline-rows">
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
                    </TransitionGroup>
                </template>
            </KsSkeleton>
        </div>

        <div v-if="legendItems.length > 0" class="timeline-legend">
            <ChartLegend
                :items="legendItems"
                :formatValue="(v: number) => String(v)"
                @toggle="onLegendToggle"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, onUnmounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {dateUtils, type FilterConfiguration, type KsBreadcrumbItem} from "@kestra-io/design-system"
    import type {ApiLightExecution, PagedResultsApiLightExecution} from "@kestra-io/kestra-sdk"

    import TimelineToolbar from "./TimelineToolbar.vue"
    import TimelineRow from "./TimelineRow.vue"
    import ChartLegend from "../../dashboard/sections/ChartLegend.vue"
    import {useExecutionsStore} from "../../../stores/executions"
    import {useExecutionsQueryScope} from "../../../composables/useExecutionsQueryScope"
    import {useTimelineRange} from "../../../composables/useTimelineRange"
    import {useExecutionFilter, useFlowExecutionFilter} from "../../filter/configurations"
    import {groupByNamespace, countByState, buildAxisTicks, isFailedLikeState, type TimelineExecution} from "../../../utils/executionsTimeline"

    const MAX_FETCHED_EXECUTIONS = 1000
    // Shared with the state filter chip in the filter bar (KsFilter's "state" key uses the same
    // IN/NOT_IN comparators), so a legend toggle also filters the table below.
    const STATE_EXCLUDE_KEY = "filters[state][NOT_IN]"
    const AXIS_TICK_COUNT = 6
    // Must match the --timeline-row-label-width custom property set below: the ResizeObserver works
    // off raw pixels, the CSS var off rem, and there is no build step to derive one from the other.
    const ROW_LABEL_WIDTH_PX = 180

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

    const drillNamespace = defineModel<string | undefined>("drillNamespace", {default: undefined})
    const drillFlowId = defineModel<string | undefined>("drillFlowId", {default: undefined})

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

    const expanded = defineModel<boolean>("expanded", {default: false})
    const loading = ref(false)
    const error = ref<string | undefined>(undefined)
    const rawExecutions = ref<TimelineExecution[]>([])
    const bodyRef = ref<HTMLElement | null>(null)
    const availableWidthPx = ref(600)

    const dimmedStates = computed<Set<string>>(() => {
        const raw = route.query[STATE_EXCLUDE_KEY]
        const value = Array.isArray(raw) ? raw[0] : raw
        return new Set((value ?? "").split(",").filter(Boolean))
    })

    const showBreadcrumb = computed(() => props.flowId === undefined)

    const scopedExecutions = computed(() => rawExecutions.value)
    const scopedFailedCount = computed(() =>
        scopedExecutions.value.filter(e => isFailedLikeState(e.state)).length,
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

    // Mirrors the [data-state="..."] selectors in TimelineBar.vue's CSS.
    const STATE_CHART_COLOR_VARS: Record<string, string> = {
        SUCCESS: "var(--ks-chart-success)",
        FAILED: "var(--ks-chart-failed)",
        WARNING: "var(--ks-chart-warning)",
        PAUSED: "var(--ks-chart-paused)",
        CANCELLED: "var(--ks-chart-cancelled)",
        SKIPPED: "var(--ks-chart-skipped)",
        CREATED: "var(--ks-chart-created)",
        RESTARTED: "var(--ks-chart-restarted)",
        RETRIED: "var(--ks-chart-retried)",
        RETRYING: "var(--ks-chart-retrying)",
        QUEUED: "var(--ks-chart-queued)",
        RUNNING: "var(--ks-chart-running)",
        KILLING: "var(--ks-chart-killing)",
        KILLED: "var(--ks-chart-killed)",
    }

    // Derived from a fetch that ignores the legend's own state-exclude filter (unlike
    // scopedExecutions), so a toggled-off state's entry stays in the legend instead of vanishing
    // the moment its executions are excluded from the main, filtered fetch.
    const legendExecutions = ref<TimelineExecution[]>([])
    const legendItems = computed(() =>
        countByState(legendExecutions.value).map(({state, count}) => ({
            label: state,
            color: STATE_CHART_COLOR_VARS[state] ?? `var(--ks-chart-${state.toLowerCase()})`,
            count,
        })),
    )

    // Within a minute of "now": close enough that the last axis tick already reads "Now", so the
    // floating marker below would just duplicate it.
    const isPinnedToNow = computed(() => Date.now() - rangeEndMs.value < 60_000)

    const nowPercent = computed(() => {
        const now = Date.now()
        const span = rangeEndMs.value - rangeStartMs.value
        if (isPinnedToNow.value || span <= 0 || now < rangeStartMs.value || now > rangeEndMs.value) return undefined
        return ((now - rangeStartMs.value) / span) * 100
    })

    const axisTicks = computed(() =>
        buildAxisTicks(rangeStartMs.value, rangeEndMs.value, AXIS_TICK_COUNT, Date.now()).map((tick, i) => ({
            key: `${i}-${tick.ms}`,
            label: tick.isNow ? t("now") : dateUtils.dateFilter(new Date(tick.ms).toISOString(), "LT"),
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

        const {[STATE_EXCLUDE_KEY]: _current, ...rest} = route.query
        router.push({
            query: next.size > 0 ? {...rest, [STATE_EXCLUDE_KEY]: [...next].join(",")} : rest,
        })
    }

    function resetFilters() {
        const {page: _p, size: _s, sort: _so, ...rest} = route.query
        const cleared = Object.fromEntries(Object.entries(rest).filter(([key]) => !key.startsWith("filters[") && key !== "q"))
        router.push({query: cleared})
    }

    const fetchedTotal = ref(0)
    const isTruncated = computed(() => fetchedTotal.value > rawExecutions.value.length)

    function mapToTimelineExecutions(results: ApiLightExecution[] | undefined): TimelineExecution[] {
        // Search executions returns the light DTO (ApiLightExecution), not the full Execution;
        // the store's findExecutions is declared Promise<any>, so this cast is what vue-tsc would
        // otherwise infer on its own.
        return (results ?? [])
            .filter((execution): execution is ApiLightExecution & {state: {startDate: string}} => Boolean(execution.state?.startDate))
            .map((execution) => ({
                id: execution.id,
                namespace: execution.namespace,
                flowId: execution.flowId,
                state: execution.state.current,
                startMs: new Date(execution.state.startDate).getTime(),
                endMs: execution.state.endDate ? new Date(execution.state.endDate).getTime() : Date.now(),
            }))
    }

    async function fetchExecutions() {
        loading.value = true
        error.value = undefined
        try {
            const query = loadQuery({
                size: MAX_FETCHED_EXECUTIONS,
                page: 1,
                sort: "state.startDate:desc",
                commit: false,
            })
            const response = await executionsStore.findExecutions(query) as PagedResultsApiLightExecution
            rawExecutions.value = mapToTimelineExecutions(response.results)
            fetchedTotal.value = response.total ?? response.results?.length ?? 0

            if (dimmedStates.value.size > 0) {
                const {[STATE_EXCLUDE_KEY]: _excluded, ...legendQuery} = query
                const legendResponse = await executionsStore.findExecutions(legendQuery) as PagedResultsApiLightExecution
                legendExecutions.value = mapToTimelineExecutions(legendResponse.results)
            } else {
                legendExecutions.value = rawExecutions.value
            }
        } catch (fetchError) {
            console.error("Failed to load timeline executions", fetchError)
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
                if (entry) availableWidthPx.value = Math.max(entry.contentRect.width - ROW_LABEL_WIDTH_PX, 100)
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
    --timeline-row-label-width: 11.25rem;

    display: flex;
    flex-direction: column;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
    box-shadow: 0 var(--ks-spacing-1) var(--ks-spacing-2) 0 var(--ks-shadow-element);
    overflow: hidden;
    margin-bottom: var(--ks-spacing-4);
    // Only the card's chrome transitions: .timeline-body's max-height swaps to `none` when
    // expanded (see below) so a namespace with many rows is never clipped, and a CSS transition
    // can't animate toward an unbounded target without a fixed pixel height to interpolate to.
    transition: border-color var(--ks-duration-slow) ease, border-radius var(--ks-duration-slow) ease,
        box-shadow var(--ks-duration-slow) ease, margin-bottom var(--ks-duration-slow) ease;
}

.executions-timeline.expanded {
    border-color: transparent;
    border-radius: 0;
    box-shadow: 0 var(--ks-spacing-1) var(--ks-spacing-2) 0 transparent;
    margin-bottom: 0;
}

@media (prefers-reduced-motion: reduce) {
    .executions-timeline {
        transition: none;
    }
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
    font-variant-numeric: tabular-nums;
}

.timeline-body {
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    max-height: 18.75rem;
    overflow-y: auto;
}

// A viewport-relative height rather than an exact `calc(100vh - Npx)`: this component is embedded
// with different chrome above it (top-level executions list vs. a flow/namespace page), so no single
// pixel offset is correct everywhere. The parent page drops its fitHeight/internal-scroll layout while
// expanded (see Executions.vue's `fitHeightResolved`), so growing this beyond its own scroll region
// grows the page itself rather than squeezing the table below it out of its flex space.
.executions-timeline.expanded .timeline-body {
    max-height: none;
    min-height: 70vh;
}

.timeline-axis {
    position: relative;
    display: flex;
    margin-left: var(--timeline-row-label-width);
    padding-bottom: var(--ks-spacing-2);
    margin-bottom: var(--ks-spacing-2);
    border-bottom: 1px dashed var(--ks-border-default);
}

.timeline-axis-tick {
    flex: 1;
    font-size: var(--ks-font-size-2xs);
    color: var(--ks-text-muted);
    text-align: left;
    font-variant-numeric: tabular-nums;

    &:last-child {
        flex: 0;
        text-align: right;
        white-space: nowrap;
    }
}

.timeline-axis-now {
    position: absolute;
    bottom: 100%;
    transform: translateX(-50%);
    font-size: var(--ks-font-size-2xs);
    font-weight: 700;
    color: var(--ks-bg-surface);
    background: var(--ks-text-primary);
    border-radius: var(--ks-radius-xs);
    padding: 0 var(--ks-spacing-1);
    white-space: nowrap;
}

.timeline-row-enter-active {
    transition: opacity var(--ks-duration-base) ease;
}

.timeline-row-leave-active {
    transition: opacity var(--ks-duration-fast) ease;
}

.timeline-row-enter-from,
.timeline-row-leave-to {
    opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
    .timeline-row-enter-active,
    .timeline-row-leave-active {
        transition: none;
    }
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
    // Mirrors a single-lane TimelineRow's rendered height (LANE_HEIGHT_REM + its own vertical
    // padding) so real rows don't jump the layout when they replace the skeleton.
    min-height: calc(1.75rem + var(--ks-spacing-2) * 2);
}

.empty-actions {
    display: flex;
    justify-content: center;
    gap: var(--ks-spacing-2);
    margin-top: var(--ks-spacing-2);
}

.timeline-legend {
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
}
</style>
