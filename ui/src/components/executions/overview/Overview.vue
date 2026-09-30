<template>
    <div v-if="execution" class="wrapper">
        <KsCard class="banner" shadow="always">
            <Banner :execution />
        </KsCard>
        <div id="alerts">
            <ErrorAlert
                v-if="execution.state.current === State.FAILED"
                :execution
            />
        </div>

        <div class="chart-header">
            <div class="chart-heading">
                <span class="chart-title">{{ chartTitle }}</span>
                <span class="chart-subtitle">{{ chartSubtitle }}</span>
            </div>
            <KsSegmented
                v-if="!isEmptyState"
                v-model="activeChart"
                :options="switcherOptions"
                :disabled="isLoading"
                :aria-disabled="isLoading"
                :aria-label="$t('overviewChart.switcherLabel')"
                @change="onSwitcherChange"
            />
        </div>

        <span v-if="isLoading" class="chart-loading-status" role="status" aria-live="polite">
            {{ $t("overviewChart.loading") }}
        </span>

        <KsAlert v-if="showAdaptiveNotice" type="info" :closable="false" class="chart-notice">
            <template #title>
                {{ $t("overviewChart.noticeTitle") }}
            </template>
            <div class="chart-notice-body">
                <span>{{ $t("overviewChart.noticeBody", {count: nodeCount}) }}</span>
                <div class="chart-notice-actions">
                    <KsButton link size="small" @click="showTopologyAnyway">
                        {{ $t("overviewChart.showTopologyAnyway") }}
                    </KsButton>
                    <KsButton
                        square
                        link
                        size="small"
                        :icon="CloseIcon"
                        :aria-label="$t('overviewChart.dismissNotice')"
                        :tooltip="$t('overviewChart.dismissNotice')"
                        @click="dismissNotice"
                    />
                </div>
            </div>
        </KsAlert>

        <div class="chart-body" :aria-busy="isLoading">
            <KsSkeleton v-if="isLoading" class="chart-panel" animated :rows="6" />
            <KsEmpty
                v-else-if="isEmptyState"
                class="chart-panel"
                :description="$t('overviewChart.emptyDescription')"
            />
            <div class="chart-panel" v-show="showData && activeChart === 'topology'">
                <Topology :horizontalDefault="!verticalLayout" />
            </div>
            <div v-if="showData && activeChart === 'gantt'" class="chart-panel">
                <Gantt embed />
            </div>
            <div v-if="showData && activeChart === 'logs'" class="chart-panel">
                <Logs embedded />
            </div>
        </div>

        <PrevNext :execution />
    </div>
    <KsNoData
        v-else
        id="empty"
        :description="$t('execution not found', {executionId: route.params.id})"
    />
</template>

<script setup lang="ts">
    import {onMounted, computed, ref, watch, markRaw} from "vue"
    import {useI18n} from "vue-i18n"

    import {useRoute} from "vue-router"
    const route = useRoute()

    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

    import {useExecutionsStore} from "../../../stores/executions"
    const store = useExecutionsStore()

    import {State} from "@kestra-io/design-system"

    import Banner from "./components/Banner.vue"
    import ErrorAlert from "./components/main/ErrorAlert.vue"
    import PrevNext from "./components/main/PrevNext.vue"
    import Topology from "../Topology.vue"
    import Gantt from "../Gantt.vue"
    import Logs from "../Logs.vue"

    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
    import ChartTimeline from "vue-material-design-icons/ChartTimeline.vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import CloseIcon from "vue-material-design-icons/Close.vue"

    import {
        chartByFlowStore,
        chartNoticeDismissedByFlowStore,
        resolveOverviewChart,
        type OverviewChart,
    } from "./chartPreference"

    const {t} = useI18n()

    const execution = computed(() => store.execution)
    const flowGraph = computed(() => store.flowGraph)
    const nodeCount = computed(() => flowGraph.value?.nodes?.length ?? 0)

    const loadExecution = (id: string) => store.loadExecution({id})

    onMounted(() => {
        if (!route.params.id) return
        // The route guard loads the execution into the store before this page mounts
        // (EXECUTION_ENTITY_META), and the SSE stream keeps it current from there.
        if (execution.value?.id === route.params.id) return
        loadExecution(route.params.id as string)
    })

    // Reset per execution: the previous execution's graph survives a PrevNext navigation and would
    // otherwise read as ready.
    const graphReady = ref(false)
    watch(() => execution.value?.id, () => {
        graphReady.value = false
    })
    watch(flowGraph, () => {
        graphReady.value = true
    })
    const isLoading = computed(() => !graphReady.value)

    // Topology comes from the flow definition rather than execution progress, so it alone survives
    // the empty state.
    const isEmptyState = computed(() =>
        !isLoading.value && (execution.value?.taskRunList?.length ?? 0) === 0,
    )
    const showData = computed(() => !isLoading.value && !isEmptyState.value)

    const currentFlow = computed(() => {
        const exec = execution.value
        return exec ? {namespace: exec.namespace, flowId: exec.flowId} : undefined
    })

    const activeChart = ref<OverviewChart>("topology")
    const adaptiveRuleFired = ref(false)
    const noticeDismissedForFlow = ref(false)
    const resolvedExecutionId = ref<string | undefined>(undefined)

    watch([() => execution.value?.id, graphReady], ([executionId, ready]) => {
        const flow = currentFlow.value
        if (!executionId || !ready || !flow) return
        if (resolvedExecutionId.value === executionId) return
        resolvedExecutionId.value = executionId

        const stored = chartByFlowStore.get(flow)
        const result = resolveOverviewChart(stored, nodeCount.value)
        activeChart.value = result.chart
        adaptiveRuleFired.value = result.adaptiveRuleFired
        noticeDismissedForFlow.value = !!chartNoticeDismissedByFlowStore.get(flow)
    }, {immediate: true})

    const showAdaptiveNotice = computed(() =>
        showData.value && adaptiveRuleFired.value && !noticeDismissedForFlow.value,
    )

    function setChart(chart: OverviewChart) {
        activeChart.value = chart
        adaptiveRuleFired.value = false
        if (!currentFlow.value) return
        chartByFlowStore.set(currentFlow.value, chart)
    }

    function onSwitcherChange(value: string | number | boolean) {
        setChart(value as OverviewChart)
    }

    function showTopologyAnyway() {
        setChart("topology")
    }

    function dismissNotice() {
        if (!currentFlow.value) return
        chartNoticeDismissedByFlowStore.set(currentFlow.value, true)
        noticeDismissedForFlow.value = true
    }

    const switcherOptions = computed(() => [
        {label: t("topology"), value: "topology", icon: markRaw(FileTreeOutline)},
        {label: t("gantt"), value: "gantt", icon: markRaw(ChartTimeline)},
        {label: t("logs"), value: "logs", icon: markRaw(FileDocumentOutline)},
    ])

    const chartTitle = computed(() => t(activeChart.value))
    const chartSubtitle = computed(() => t(`overviewChart.${activeChart.value}Subtitle`))

    defineOptions({inheritAttrs: false})
</script>

<style scoped lang="scss">
    .wrapper {
        display: flex;
        flex-direction: column;
        height: 100%;
        gap: var(--ks-spacing-4);
    }

    .banner {
        flex-shrink: 0;
        width: 100%;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);

        :deep(.kel-card__body) {
            height: 100%;
            padding: 0;
        }
    }

    #alerts:empty {
        display: none;
    }

    .chart-loading-status {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
    }

    .chart-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--ks-spacing-3);
        flex-shrink: 0;
    }

    .chart-heading {
        display: flex;
        flex-direction: column;
        min-width: 0;
    }

    .chart-title {
        font-weight: 700;
        font-size: var(--ks-font-size-lg);
        color: var(--ks-text-primary);
    }

    .chart-subtitle {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .chart-notice {
        flex-shrink: 0;
    }

    .chart-notice-body {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--ks-spacing-3);
    }

    .chart-notice-actions {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        flex-shrink: 0;
    }

    .chart-body {
        flex: 1;
        // Floor so a short viewport still gets a usable chart; the page scrolls from there.
        min-height: 400px;
        display: flex;
        flex-direction: column;
    }

    .chart-panel {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
    }

    #empty {
        height: 100%;
        background-color: var(--ks-bg-elevated);
    }
</style>
