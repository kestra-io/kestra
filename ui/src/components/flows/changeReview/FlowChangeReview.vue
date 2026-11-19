<template>
    <div data-test="flow-change-review">
        <FlowChangeSummary class="review-summary" :diff="diff" />
        <FlowChangeGraph
            class="review-graph"
            :loading="loading"
            :error="graphError"
            :source="after ?? before ?? ''"
            :graph="merged?.graph"
            :diffStatuses="merged?.statusByUid ?? new Map()"
            :selectedNodeUid="selectedNodeUid"
            @select-node="selectedId = stringUtils.afterLastDot($event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {stringUtils} from "@kestra-io/design-system"
    import {diffFlowSources, mergeFlowGraphs, type FlowGraph} from "@kestra-io/topology"
    import FlowChangeGraph from "./FlowChangeGraph.vue"
    import FlowChangeSummary from "./FlowChangeSummary.vue"
    import {useFlowStore} from "../../../stores/flow"

    const props = defineProps<{
        before?: string;
        after?: string;
    }>()

    const flowStore = useFlowStore()

    const selectedId = ref<string>()
    const diff = computed(() => diffFlowSources(props.before, props.after))

    const graphs = ref<{before?: FlowGraph; after?: FlowGraph}>({})
    const loading = ref(false)
    const graphError = ref(false)
    let latest = 0

    async function loadGraphs() {
        const request = ++latest
        const load = (flow?: string) => (flow ? flowStore.getGraphFromSourceResponse({flow}) : undefined)
        loading.value = true
        graphError.value = false
        try {
            const [before, after] = await Promise.all([load(props.before), load(props.after)])
            if (request === latest) graphs.value = {before, after}
        } catch {
            if (request === latest) graphError.value = true
        } finally {
            if (request === latest) loading.value = false
        }
    }

    watch(() => [props.before, props.after], loadGraphs, {immediate: true})

    const merged = computed(() => (graphs.value.before || graphs.value.after
        ? mergeFlowGraphs(graphs.value.before, graphs.value.after, diff.value)
        : undefined))

    const selectedNodeUid = computed(() => [...merged.value?.statusByUid.keys() ?? []]
        .find((uid) => stringUtils.afterLastDot(uid) === selectedId.value))
</script>

<style scoped>
    .review-summary {
        margin-bottom: var(--ks-spacing-2);
    }

    .review-graph {
        height: 24rem;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }
</style>
