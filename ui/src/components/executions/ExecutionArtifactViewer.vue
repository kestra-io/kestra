<template>
    <section class="artifacts-viewer" data-test="execution-artifact-viewer">
        <header class="artifacts-viewer-header">
            <span class="artifacts-task-icon"><TaskIcon :cls="artifact.cls" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" onlyIcon /></span>
            <div>
                <KsText tag="strong">{{ artifact.title }}</KsText>
                <KsText size="small" type="secondary">{{ artifact.taskId }}</KsText>
            </div>
            <KsSelect
                v-if="iterationRuns.length > 1"
                :modelValue="taskRunId"
                size="small"
                class="artifacts-iteration-select"
                data-test="execution-artifact-iteration-select"
                :aria-label="$t('iteration')"
                @update:modelValue="emit('selectIteration', String($event))"
            >
                <KsOption
                    v-for="(run, index) in iterationRuns"
                    :key="run.id"
                    :value="run.id"
                    :label="labelFor(run, index + 1)"
                    :data-test="`execution-artifact-iteration-${run.id}`"
                />
            </KsSelect>
            <KsSegmented
                v-if="siblings.length > 1"
                :modelValue="artifact.key"
                class="artifacts-switch"
                variant="neutral"
                :options="siblingOptions"
                @update:modelValue="emit('select', String($event))"
            />
            <KsIconButton
                v-if="fullscreen !== undefined"
                :tooltip="fullscreenLabel"
                :aria-label="fullscreenLabel"
                :aria-pressed="fullscreen"
                filled
                data-test="execution-artifact-fullscreen"
                @click="emit('toggleFullscreen')"
            >
                <component :is="fullscreen ? FullscreenExit : Fullscreen" />
            </KsIconButton>
        </header>
        <div class="artifacts-viewer-body">
            <RemoteComponent
                v-if="hasResolvedComponent(artifact.taskType)"
                :key="`${artifact.key}-${taskRunId ?? ''}`"
                :taskType="artifact.taskType"
                :task="task"
                :execution="execution"
                :namespace="execution?.namespace"
                :flowId="execution?.flowId"
                :tenant="tenant"
                :progress="progress"
                v-bind="fetchers"
            />
            <KsAlert v-else-if="hasFailedComponent(artifact.taskType)" type="error" :closable="false" data-test="execution-artifact-error">
                {{ $t("plugins.ui_load_error") }}
            </KsAlert>
            <KsSkeleton v-else animated :rows="4" data-test="execution-artifact-loading" />
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsAlert, KsIconButton, KsOption, KsSegmented, KsSelect, KsSkeleton, KsText} from "@kestra-io/design-system"
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import FullscreenExit from "vue-material-design-icons/FullscreenExit.vue"
    import TaskIcon from "../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import type {Execution} from "../../stores/executions"
    import {EXECUTION_ARTIFACTS_KEY, type ExecutionArtifact} from "./composables/useExecutionArtifacts"
    import {useArtifactFetchers} from "./composables/useArtifactFetchers"
    import {useProgressStream} from "./composables/useProgressStream"
    import {useExecutionsStore} from "../../stores/executions"
    import {iterationLabel} from "./iterationLabel"

    const props = withDefaults(defineProps<{
        artifact: ExecutionArtifact
        siblings: ExecutionArtifact[]
        execution?: Execution
        taskRunId?: string
        fullscreen?: boolean
    }>(), {
        execution: undefined,
        taskRunId: undefined,
        fullscreen: undefined,
    })

    const emit = defineEmits<{
        select: [key: string]
        selectIteration: [taskRunId: string]
        toggleFullscreen: []
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const pluginsStore = usePluginsStore()
    pluginsStore.fetchIcons()

    const {RemoteComponent, tasks, hasResolvedComponent, hasFailedComponent} = inject(EXECUTION_ARTIFACTS_KEY)!
    const {runsOf, fetchOutputs, fetchMetrics, fetchEndpoint} = useArtifactFetchers(() => props.execution, () => props.taskRunId)
    const executionsStore = useExecutionsStore()
    useProgressStream(() => props.execution)

    const tenant = computed(() => typeof route.params.tenant === "string" ? route.params.tenant : undefined)
    const task = computed(() => tasks.value.find((candidate) => candidate.id === props.artifact.taskId))
    const progress = computed(() => {
        const runId = props.taskRunId ?? runsOf(props.artifact.taskId).at(-1)?.id
        return runId ? executionsStore.progressEvents.filter((event) => event.taskRunId === runId) : []
    })
    const siblingOptions = computed(() => props.siblings.map((sibling) => ({label: sibling.title, value: sibling.key})))
    const fullscreenLabel = computed(() => t(props.fullscreen ? "execution_artifacts.exit_fullscreen" : "execution_artifacts.fullscreen"))

    const iterationRuns = computed(() => props.execution?.taskRunList?.filter((run) => run.taskId === props.artifact.taskId) ?? [])
    const labelFor = (run: {value?: unknown}, position: number) => iterationLabel(t, run, position)

    // Built once per selection: a plugin watching these props must not reload on every execution update.
    const fetchers = computed(() => ({
        fetchOutputs: fetchOutputs(props.artifact.taskId),
        fetchMetrics: fetchMetrics(props.artifact.taskId),
        fetchEndpoint: fetchEndpoint({id: props.artifact.taskId, type: props.artifact.cls}),
    }))
</script>

<style scoped>
    .artifacts-task-icon {
        flex: 0 0 auto;
        width: 1.75rem;
        height: 1.75rem;
    }

    .artifacts-switch {
        margin-left: auto;
    }

    .artifacts-iteration-select {
        min-width: 9rem;
        max-width: 14rem;
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
