<template>
    <RemoteComponent
        v-if="hasResolvedComponent(artifact.taskType)"
        :taskType="artifact.taskType"
        :task="task"
        :execution="execution"
        :namespace="execution?.namespace"
        :flowId="execution?.flowId"
        :tenant="tenant"
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
    import type {Execution} from "../../stores/executions"
    import {artifactFetchers} from "./composables/useArtifactFetchers"
    import {EXECUTION_ARTIFACTS_KEY, type ExecutionArtifact} from "./composables/useExecutionArtifacts"

    const props = defineProps<{
        artifact: ExecutionArtifact
        execution?: Execution
    }>()

    const route = useRoute()
    const {RemoteComponent, tasks, hasResolvedComponent, hasFailedComponent} = inject(EXECUTION_ARTIFACTS_KEY)!

    const tenant = computed(() => typeof route.params.tenant === "string" ? route.params.tenant : undefined)
    const task = computed(() => tasks.value.find((candidate) => candidate.id === props.artifact.taskId))

    const {fetchOutputs, fetchMetrics, fetchEndpoint} = artifactFetchers(() => props.execution)

    // Built once per artifact: a plugin watching these props must not reload on every execution update.
    const fetchers = computed(() => ({
        fetchOutputs: fetchOutputs(props.artifact.taskId),
        fetchMetrics: fetchMetrics(props.artifact.taskId),
        fetchEndpoint: fetchEndpoint({id: props.artifact.taskId, type: props.artifact.cls}),
    }))
</script>
