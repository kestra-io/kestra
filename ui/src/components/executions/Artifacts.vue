<template>
    <KsSkeleton v-if="!manifestReady" animated :rows="4" data-test="execution-artifacts-loading" />
    <KsEmpty v-else-if="!artifacts.length" data-test="execution-artifacts-empty">
        <template #description>
            {{ $t("execution_artifacts.empty") }}
        </template>
    </KsEmpty>
    <div v-else class="artifacts-layout">
        <nav class="artifacts-list" :aria-label="$t('execution_artifacts.title')">
            <KsText size="small" class="artifacts-count muted">
                {{ countLabel }}
            </KsText>
            <section v-for="group in groups" :key="group.taskId" class="artifacts-group">
                <div class="artifacts-group-header">
                    <span class="artifacts-task-icon"><TaskIcon :cls="group.cls" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" onlyIcon /></span>
                    <KsText tag="strong">{{ group.taskId }}</KsText>
                    <KsText size="small" class="muted">{{ afterLastDot(group.cls) }}</KsText>
                    <KsText v-if="durations[group.taskId]" size="small" class="muted artifacts-duration">{{ durations[group.taskId] }}</KsText>
                </div>
                <KsSelectableListItem
                    v-for="artifact in group.artifacts"
                    :key="artifact.key"
                    :title="artifact.title"
                    :subtitle="subtitles[artifact.key] ?? artifact.description"
                    :selected="artifact.key === selected?.key"
                    inset
                    :data-test="`execution-artifact-${artifact.key}`"
                    @click="selectedKey = artifact.key"
                >
                    <template #leading><KsIcon><FileDocumentOutline /></KsIcon></template>
                </KsSelectableListItem>
            </section>
        </nav>
        <div ref="inlineViewerTarget" class="artifacts-viewer-slot" />
        <KsDialog
            v-model="fullscreenOpen"
            :title="$t('execution_artifacts.title')"
            data-test="execution-artifact-fullscreen-dialog"
            fill
            fullscreen
        >
            <div ref="fullscreenViewerTarget" class="artifacts-viewer-slot" />
        </KsDialog>
        <Teleport v-if="viewerTarget && selected" :to="viewerTarget">
            <ExecutionArtifactViewer
                :artifact="selected"
                :siblings="siblings"
                :execution="execution"
                :fullscreen="fullscreenOpen"
                @select="selectedKey = $event"
                @toggleFullscreen="fullscreenOpen = !fullscreenOpen"
            />
        </Teleport>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref, useTemplateRef, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsDialog, KsEmpty, KsIcon, KsSelectableListItem, KsSkeleton, KsText, durationUtils, stringUtils} from "@kestra-io/design-system"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import TaskIcon from "../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {EXECUTION_ARTIFACTS_KEY, type ExecutionArtifact} from "./composables/useExecutionArtifacts"
    import {useArtifactFetchers} from "./composables/useArtifactFetchers"
    import ExecutionArtifactViewer from "./ExecutionArtifactViewer.vue"

    const {afterLastDot} = stringUtils

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const pluginsStore = usePluginsStore()
    pluginsStore.fetchIcons()
    const executionsStore = useExecutionsStore()

    const {artifacts, tasks, subtitleResolvers, manifestReady} = inject(EXECUTION_ARTIFACTS_KEY)!

    const execution = computed<Execution | undefined>(() => executionsStore.execution ?? undefined)

    const groups = computed(() => {
        const byTask = new Map<string, {taskId: string, cls: string, artifacts: ExecutionArtifact[]}>()
        for (const artifact of artifacts.value) {
            const group = byTask.get(artifact.taskId) ?? {taskId: artifact.taskId, cls: artifact.cls, artifacts: []}
            group.artifacts.push(artifact)
            byTask.set(artifact.taskId, group)
        }
        return [...byTask.values()]
    })

    const countLabel = computed(() => t("execution_artifacts.count", {
        artifacts: t("execution_artifacts.artifacts", {count: artifacts.value.length}, artifacts.value.length),
        tasks: t("execution_artifacts.tasks", {count: groups.value.length}, groups.value.length),
    }))

    const inlineViewerTarget = useTemplateRef<HTMLElement>("inlineViewerTarget")
    const fullscreenViewerTarget = useTemplateRef<HTMLElement>("fullscreenViewerTarget")
    const fullscreenOpen = ref(false)
    const viewerTarget = computed(() => fullscreenOpen.value
        ? fullscreenViewerTarget.value ?? inlineViewerTarget.value
        : inlineViewerTarget.value)

    const selectedKey = computed({
        get: () => typeof route.query.artifact === "string" ? route.query.artifact : undefined,
        set: (key) => router.replace({query: {...route.query, artifact: key}}),
    })
    const selected = computed(() => artifacts.value.find((a) => a.key === selectedKey.value) ?? artifacts.value[0])
    const siblings = computed(() => artifacts.value.filter((a) => a.taskId === selected.value?.taskId))

    const {runsOf, fetchOutputs, fetchMetrics, fetchEndpoint} = useArtifactFetchers(() => execution.value)

    // A task inside a loop runs once per iteration: the header shows the total, the viewer reads the latest run.
    const durations = computed(() => Object.fromEntries(groups.value.flatMap(({taskId}) => {
        const seconds = runsOf(taskId).reduce((sum, run) => sum + (run.state?.duration ? durationUtils.duration(run.state.duration) : 0), 0)
        return seconds ? [[taskId, durationUtils.humanDuration(seconds)]] : []
    })))

    const subtitles = ref<Record<string, string | undefined>>({})
    const subtitleSequences = new Map<string, number>()
    const artifactKeys = computed(() => artifacts.value.map((a) => a.key).join())
    const taskRunStates = computed(() => (execution.value?.taskRunList ?? []).map((run) => `${run.id}${run.state?.current}`).join())

    // Re-read when a task run changes state: outputs a subtitle summarises only exist once the task ran.
    watch([artifactKeys, () => Object.keys(subtitleResolvers).length, taskRunStates], () => {
        const outputsByRun = new Map<string, Promise<unknown>>()
        const sharedFetchOutputs = (taskId: string) => (options: {taskRunId?: string} = {}) => {
            const cacheKey = `${taskId}:${options.taskRunId ?? ""}`
            if (!outputsByRun.has(cacheKey)) outputsByRun.set(cacheKey, fetchOutputs(taskId)(options))
            return outputsByRun.get(cacheKey)!
        }

        for (const artifact of artifacts.value) {
            const resolve = subtitleResolvers[artifact.taskType] as ((ctx: unknown) => unknown) | undefined
            const task = tasks.value.find((t) => t.id === artifact.taskId)
            if (!resolve || !task) continue
            const sequence = (subtitleSequences.get(artifact.key) ?? 0) + 1
            subtitleSequences.set(artifact.key, sequence)
            Promise.resolve(resolve({task, execution: execution.value, fetchOutputs: sharedFetchOutputs(task.id), fetchMetrics: fetchMetrics(task.id), fetchEndpoint: fetchEndpoint(task)}))
                .then((subtitle) => {
                    if (subtitleSequences.get(artifact.key) !== sequence) return
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

    .artifacts-task-icon {
        flex: 0 0 auto;
        width: 1.5rem;
        height: 1.5rem;
    }

    .artifacts-duration {
        margin-left: auto;
    }

    .muted {
        color: var(--ks-text-secondary);
    }

    .artifacts-count {
        padding: var(--ks-spacing-2);
        text-transform: uppercase;
    }

    .artifacts-group {
        display: flex;
        flex-direction: column;
    }

    .artifacts-group-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2);
    }

    .artifacts-viewer-slot {
        display: flex;
        flex: 1;
        min-width: 0;
        min-height: 0;
    }
</style>
