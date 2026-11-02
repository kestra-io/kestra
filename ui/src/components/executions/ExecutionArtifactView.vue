<template>
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
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useRoute} from "vue-router"
    import {KsAlert, KsSkeleton} from "@kestra-io/design-system"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {useArtifactFetchers} from "./composables/useArtifactFetchers"
    import {useProgressStream} from "./composables/useProgressStream"
    import {EXECUTION_ARTIFACTS_KEY, type ExecutionArtifact} from "./composables/useExecutionArtifacts"

    const props = withDefaults(defineProps<{
        artifact: ExecutionArtifact
        execution?: Execution
        taskRunId?: string
    }>(), {
        execution: undefined,
        taskRunId: undefined,
    })

    const route = useRoute()
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

    // Built once per selection: a plugin watching these props must not reload on every execution update.
    const fetchers = computed(() => ({
        fetchOutputs: fetchOutputs(props.artifact.taskId),
        fetchMetrics: fetchMetrics(props.artifact.taskId),
        fetchEndpoint: fetchEndpoint({id: props.artifact.taskId, type: props.artifact.cls}),
    }))
</script>
