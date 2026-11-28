<template>
    <div v-ks-loading="loading" class="flow-change-graph">
        <KsAlert
            v-if="error"
            type="warning"
            :closable="false"
            :title="$t('flow_change_review.graph_error')"
        />
        <Topology
            v-else-if="graph"
            :id="topologyId"
            :source="source"
            :flowGraph="graph"
            :diffStatuses="diffStatuses"
            :selectedNodeUid="selectedNodeUid"
            :isHorizontal="isHorizontal"
            toggleOrientationButton
            :icons="pluginsStore.icons"
            :loadIcon="pluginsStore.loadIcon"
            @toggle-orientation="isHorizontal = !isHorizontal"
            @select-node="emit('select-node', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref, useId} from "vue"
    import {Topology, type FlowGraph, type TaskDiffStatus} from "@kestra-io/topology"
    import {usePluginsStore} from "../../../stores/plugins"

    defineProps<{
        loading: boolean;
        error: boolean;
        source: string;
        graph?: FlowGraph;
        diffStatuses: Map<string, TaskDiffStatus>;
        selectedNodeUid?: string;
    }>()

    const emit = defineEmits<{"select-node": [uid: string]}>()

    const pluginsStore = usePluginsStore()
    const topologyId = `flow-change-graph-${useId()}`
    const isHorizontal = ref(false)

    onMounted(() => pluginsStore.fetchIcons())
</script>

<style scoped>
    .flow-change-graph {
        position: relative;
        min-width: 0;
        min-height: 0;
        height: 100%;
    }
</style>
