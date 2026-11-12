<template>
    <div class="execution-inputs-preview" data-test="execution-inputs-preview">
        <template v-if="customViewers.length > 0">
            <div v-for="{id, viewer, value} in customViewers" :key="id">
                <code>{{ id }}</code>
                <component :is="viewer" v-if="viewer" :value="value" />
                <pre v-else class="inputs-json" data-test="execution-inputs-json">{{ JSON.stringify(value, null, "\t") }}</pre>
            </div>
        </template>
        <pre v-else class="inputs-json" data-test="execution-inputs-json">{{ JSON.stringify(inputs, null, "\t") }}</pre>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import type {FlowForExecution} from "@kestra-io/kestra-sdk"
    import {useExecutionsStore} from "../../stores/executions"
    import {handled} from "../../utils/kestraHttp"
    import {useVariableValueRenderer} from "override/components/executions/outputs/variableValueExtension"

    const props = defineProps<{
        namespace: string;
        flowId: string;
        flowRevision?: number;
        inputs: Record<string, unknown>;
    }>()

    const executionsStore = useExecutionsStore()
    const variableValueRenderer = useVariableValueRenderer()
    const flow = ref<FlowForExecution>()

    loadFlow(props.namespace, props.flowId, props.flowRevision)
        .then(loaded => flow.value = loaded)
        .catch(handled)

    const customViewers = computed(() => {
        const entries = Object.entries(props.inputs).map(([id, value]) => ({id, value, viewer: variableValueRenderer(`inputs.${id}`, flow.value)}))
        return entries.some(entry => entry.viewer !== undefined) ? entries : []
    })

    function loadFlow(namespace: string, flowId: string, revision?: number): Promise<FlowForExecution> {
        const key = `${namespace}/${flowId}/${revision}`
        let pending = flowCache.get(key)
        if (!pending) {
            pending = executionsStore.loadFlowForExecution({namespace, flowId, revision, store: false})
            pending.catch(() => flowCache.delete(key))
            flowCache.set(key, pending)
        }
        return pending
    }
</script>

<script lang="ts">
    // Hovering one row per execution of the same flow revision loads that flow once.
    const flowCache = new Map<string, Promise<FlowForExecution>>()
</script>

<style scoped>
    .execution-inputs-preview {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .inputs-json {
        margin-bottom: 0;
    }
</style>
