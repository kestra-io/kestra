<template>
    <aside
        v-if="node"
        class="run-panel"
        :aria-label="$t('gantt_panel.label')"
        data-test="gantt-run-panel"
    >
        <header class="panel-head">
            <div class="panel-top">
                <nav class="lineage" :aria-label="$t('gantt_panel.lineage')">
                    <template v-for="crumb in crumbs" :key="crumb.key">
                        <button
                            v-if="crumb.iteration"
                            type="button"
                            class="crumb crumb-iteration"
                            data-test="gantt-panel-crumb"
                            @click="tree.select(crumb.iteration, {reveal: true})"
                        >
                            {{ crumb.label }}
                        </button>
                        <span v-else class="crumb">{{ crumb.label }}</span>
                        <span class="crumb-separator" aria-hidden="true">›</span>
                    </template>
                    <span class="crumb-current" aria-current="location">{{ currentLabel }}</span>
                </nav>
                <KsIconButton :aria-label="$t('close')" data-test="gantt-panel-close" @click="tree.closePanel()">
                    <Close />
                </KsIconButton>
            </div>

            <div class="panel-title">
                <span class="title-main">
                    <span v-if="node.kind === 'iteration'" class="iteration-index">#{{ node.row.index + 1 }}</span>
                    <GanttTaskIcon v-else :taskType="node.taskType" />
                    <h2>{{ title }}</h2>
                    <KsExecutionStatus :status="state" />
                </span>
                <span class="title-meta" data-test="gantt-panel-meta">
                    <template v-if="node.kind === 'loop'">
                        <template v-if="loopTotal !== undefined">
                            {{ $t("gantt_loop_iterations_count", {count: loopTotal}, loopTotal) }} ·
                        </template>
                    </template>
                    <template v-else>{{ offset(histories) }} ·</template>
                    <Duration :histories="histories" />
                </span>
            </div>

            <GanttIterationStepper v-if="owningIteration(node)" :node="node" />
        </header>

        <div class="panel-body">
            <GanttPanelLoop v-if="node.kind === 'loop'" :key="node.key" :node="node" />
            <GanttPanelIteration v-else-if="node.kind === 'iteration'" :key="node.key" :node="node" />
            <GanttPanelTaskRun v-else :node="node" />
        </div>
    </aside>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import Close from "vue-material-design-icons/Close.vue"
    import {Duration} from "@kestra-io/topology"
    import {KsExecutionStatus, KsIconButton} from "@kestra-io/design-system"
    import {useExecutionsStore} from "../../../stores/executions"
    import GanttTaskIcon from "./GanttTaskIcon.vue"
    import GanttIterationStepper from "./GanttIterationStepper.vue"
    import GanttPanelLoop from "./GanttPanelLoop.vue"
    import GanttPanelIteration from "./GanttPanelIteration.vue"
    import GanttPanelTaskRun from "./GanttPanelTaskRun.vue"
    import {useGanttTimeline} from "../ganttTimeline"
    import {iterationLabel, loopRefOf, owningIteration, useGanttTree, type Ancestor} from "./ganttTree"

    const tree = useGanttTree()
    const executionsStore = useExecutionsStore()
    const {offset} = useGanttTimeline()

    const node = computed(() => tree.selected.value)

    const crumbs = computed<Ancestor[]>(() => [
        {key: "flow", label: executionsStore.execution?.flowId ?? ""},
        ...(node.value?.ancestors ?? []),
    ])
    const title = computed(() => {
        const current = node.value
        if (!current) return ""
        return current.kind === "iteration" ? current.row.value : current.taskRun.taskId
    })
    const currentLabel = computed(() => {
        const current = node.value
        if (!current) return ""
        return current.kind === "iteration" ? iterationLabel(current.row) : current.taskRun.taskId
    })
    const state = computed(() => {
        const current = node.value
        if (!current) return ""
        return current.kind === "iteration" ? current.row.state.current : current.taskRun.state.current
    })
    const histories = computed(() => {
        const current = node.value
        if (!current) return []
        return current.kind === "iteration" ? current.row.state.histories : current.taskRun.state.histories
    })
    const loopHandle = computed(() => node.value?.kind === "loop" ? tree.loopHandle(loopRefOf(node.value)) : undefined)
    const loopTotal = computed(() => loopHandle.value?.totalCount.value)

    watch(loopHandle, (handle) => handle?.loadCounts(), {immediate: true})
</script>

<style scoped>
    .run-panel {
        height: 100%;
        display: flex;
        flex-direction: column;
        overflow: hidden;
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
    }

    .panel-head {
        flex-shrink: 0;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border-bottom: var(--ks-border-width-thin) solid var(--ks-border-default);
    }

    .panel-top {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    .lineage {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-1);
        min-width: 0;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
        color: var(--ks-text-secondary);
    }

    .crumb-separator {
        color: var(--ks-text-muted);
    }

    .crumb-iteration {
        padding: 0 var(--ks-spacing-1);
        border: 0;
        border-radius: var(--ks-radius-xs);
        background: transparent;
        font: inherit;
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover);
        }

        &:focus-visible {
            outline: var(--ks-border-width-base) solid var(--ks-border-focus);
        }
    }

    .crumb-current {
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .panel-title {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    .title-main {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;

        h2 {
            margin: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-family: var(--ks-font-family-mono);
            font-size: var(--ks-font-size-lg);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }
    }

    .title-meta {
        flex-shrink: 0;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
        color: var(--ks-text-secondary);
        white-space: nowrap;
    }

    .iteration-index {
        display: grid;
        place-items: center;
        flex-shrink: 0;
        min-width: 1.75rem;
        height: 1.75rem;
        padding: 0 var(--ks-spacing-1);
        border: var(--ks-border-width-thin) dashed var(--ks-border-strong);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-base);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
    }

    .panel-body {
        flex: 1;
        min-height: 0;
        overflow: auto;
    }
</style>
