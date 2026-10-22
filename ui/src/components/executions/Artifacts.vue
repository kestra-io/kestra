<template>
    <KsEmpty v-if="!artifacts.length" data-test="execution-artifacts-empty">
        <template #description>
            {{ $t("execution_artifacts.empty") }}
        </template>
    </KsEmpty>
    <div v-else class="artifacts-layout">
        <nav class="artifacts-list" :aria-label="$t('execution_artifacts.title')">
            <KsText size="small" class="artifacts-count muted">
                {{ $t("execution_artifacts.count", {artifacts: artifacts.length, tasks: groups.length}, artifacts.length) }}
            </KsText>
            <section v-for="group in groups" :key="group.taskId" class="artifacts-group">
                <div class="artifacts-group-header">
                    <TaskIcon :cls="group.taskType" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" onlyIcon />
                    <KsText tag="strong">{{ group.taskId }}</KsText>
                    <KsText size="small" class="muted">{{ afterLastDot(group.taskType) }}</KsText>
                </div>
                <button
                    v-for="artifact in group.artifacts"
                    :key="artifact.key"
                    type="button"
                    class="artifacts-item"
                    :class="{selected: artifact.key === selected?.key}"
                    :aria-current="artifact.key === selected?.key"
                    :data-test="`execution-artifact-${artifact.key}`"
                    @click="selectedKey = artifact.key"
                >
                    <KsText>{{ artifact.title }}</KsText>
                    <KsText v-if="subtitles[artifact.key] ?? artifact.description" size="small" class="muted">{{ subtitles[artifact.key] ?? artifact.description }}</KsText>
                </button>
            </section>
        </nav>
        <section v-if="selected" class="artifacts-viewer" data-test="execution-artifact-viewer">
            <header class="artifacts-viewer-header">
                <TaskIcon :cls="selected.taskType.split('/')[0]" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" onlyIcon />
                <div>
                    <KsText tag="strong">{{ selected.title }}</KsText>
                    <KsText size="small" class="muted">{{ selected.taskId }}</KsText>
                </div>
            </header>
            <div class="artifacts-viewer-body">
                <RemoteComponent
                    :key="selected.key"
                    :taskType="selected.taskType"
                    :task="selectedTask"
                    :execution="execution"
                    :namespace="execution?.namespace"
                    :flowId="execution?.flowId"
                    :tenant="tenant"
                    :fetchOutputs="fetchOutputs(selected.taskId)"
                    :fetchMetrics="fetchMetrics(selected.taskId)"
                />
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref, watch} from "vue"
    import {useRoute} from "vue-router"
    import type {Execution, PagedResultsMetricEntry} from "@kestra-io/kestra-sdk"
    import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"
    import {KsEmpty, KsText, stringUtils} from "@kestra-io/design-system"
    import TaskIcon from "../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {useExecutionsStore} from "../../stores/executions"
    import {loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"
    import {EXECUTION_ARTIFACTS_KEY} from "./composables/useExecutionArtifacts"

    const {afterLastDot} = stringUtils

    const route = useRoute()
    const pluginsStore = usePluginsStore()
    pluginsStore.fetchIcons()
    const executionsStore = useExecutionsStore()

    const {RemoteComponent, artifacts, tasks, subtitleResolvers} = inject(EXECUTION_ARTIFACTS_KEY)!

    const execution = computed(() => (executionsStore.execution ?? undefined) as unknown as Execution | undefined)
    const tenant = computed(() => typeof route.params.tenant === "string" ? route.params.tenant : undefined)

    const groups = computed(() => {
        const byTask = new Map<string, {taskId: string, taskType: string, artifacts: typeof artifacts.value}>()
        for (const artifact of artifacts.value) {
            const group = byTask.get(artifact.taskId) ?? {taskId: artifact.taskId, taskType: artifact.taskType.split("/")[0], artifacts: []}
            group.artifacts.push(artifact)
            byTask.set(artifact.taskId, group)
        }
        return [...byTask.values()]
    })

    const selectedKey = ref<string>()
    const selected = computed(() => artifacts.value.find((a) => a.key === selectedKey.value) ?? artifacts.value[0])
    const selectedTask = computed(() => tasks.value.find((task) => task.id === selected.value?.taskId))

    const currentTaskRunId = (taskId: string) =>
        execution.value?.taskRunList?.filter((run) => run.taskId === taskId).at(-1)?.id

    // Resolved when called, so an artifact opened on a still-running execution reads the latest run.
    const fetchOutputs = (taskId: string) => ({taskRunId}: {taskRunId?: string} = {}) => {
        const runId = taskRunId ?? currentTaskRunId(taskId)
        return execution.value?.id && runId ? loadTaskRunOutputs(execution.value.id, runId) : Promise.resolve({})
    }

    const fetchMetrics = (taskId: string) => ({page, size, sort, taskRunId}: {page?: number, size?: number, sort?: string, taskRunId?: string} = {}): Promise<PagedResultsMetricEntry> => {
        const executionId = execution.value?.id
        if (!executionId) return Promise.resolve({results: [], total: 0})
        return MetricsAPI.searchByExecution({
            executionId,
            taskId,
            taskRunId,
            page,
            size,
            sort: sort ? [sort] : undefined,
        })
    }

    const subtitles = ref<Record<string, string | undefined>>({})

    // Re-read when the execution moves on: outputs a subtitle summarises only exist once the task ran.
    watch([artifacts, () => Object.keys(subtitleResolvers).length, () => execution.value?.state?.current], () => {
        for (const artifact of artifacts.value) {
            const resolve = subtitleResolvers[artifact.taskType] as ((ctx: unknown) => unknown) | undefined
            const task = tasks.value.find((t) => t.id === artifact.taskId)
            if (!resolve || !task) continue
            Promise.resolve(resolve({task, execution: execution.value, fetchOutputs: fetchOutputs(task.id), fetchMetrics: fetchMetrics(task.id)}))
                .then((subtitle) => {
                    subtitles.value = {...subtitles.value, [artifact.key]: typeof subtitle === "string" ? subtitle : undefined}
                })
                .catch((error) => console.error(`[ExecutionArtifacts] getSubtitle failed for "${artifact.key}":`, error))
        }
    }, {immediate: true})
</script>

<style scoped>
    .artifacts-layout {
        display: flex;
        gap: var(--ks-spacing-4);
        min-height: 0;
        height: 100%;
        padding: var(--ks-spacing-2);
    }

    .artifacts-list {
        flex: 0 0 21rem;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2);
        overflow-y: auto;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }

    .muted {
        color: var(--ks-text-secondary);
    }

    .artifacts-count {
        padding: var(--ks-spacing-2);
        text-transform: uppercase;
    }

    .artifacts-group-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2);
    }

    .artifacts-item {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        width: calc(100% - var(--ks-spacing-4));
        margin-left: var(--ks-spacing-4);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        font: inherit;
        text-align: left;
        cursor: pointer;
        background: transparent;
        border: 1px solid transparent;
        border-radius: var(--ks-radius-base);

        &:hover {
            background: var(--ks-bg-hover);
        }

        &.selected {
            background: var(--ks-bg-active);
            border-color: var(--ks-border-focus);
        }
    }

    .artifacts-viewer {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }

    .artifacts-viewer-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-default);

        & > div {
            display: flex;
            flex-direction: column;
        }
    }

    .artifacts-viewer-body {
        flex: 1;
        min-height: 0;
        overflow: auto;
    }
</style>
