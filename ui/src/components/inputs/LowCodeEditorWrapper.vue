<template>
    <div id="topologyWrapper" v-loading="isLoading" class="vue-flow">
        <LowCodeEditor
            v-if="flowGraph"
            :flowGraph="flowGraph"
            :flowId="flowId"
            :namespace="namespace"
            :isReadOnly="isReadOnly"
            :source="flowYaml"
            :isAllowedEdit="isAllowedEdit"
            :expandedSubflows="expandedSubflows"
            @on-edit="onEdit"
            @loading="loadingState"
            @expand-subflow="onExpandSubflow"
            @swapped-task="onSwappedTask"
        />
        <div v-else-if="invalidGraph">
            <el-alert
                :title="$t('topology-graph.invalid')"
                type="error"
                class="invalid-graph"
                :closable="false"
            >
                {{ $t('topology-graph.invalid_description') }}
            </el-alert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {Utils} from "@kestra-io/ui-libs";
    import LowCodeEditor from "./LowCodeEditor.vue";
    import {useFlowStore} from "../../stores/flow";

    const flowStore = useFlowStore();

    const flowYaml = computed(() => flowStore.flowYaml);
    const flowGraph = computed(() => flowStore.flowGraph);
    const invalidGraph = computed(() => flowStore.invalidGraph);
    const flowId = computed(() => flowStore.flow?.id);
    const namespace = computed(() => flowStore.flow?.namespace);
    const expandedSubflows = computed<string[]>(() => flowStore.expandedSubflows);
    const isAllowedEdit = computed(() => flowStore.isAllowedEdit);
    const isReadOnly = computed(() => flowStore.isReadOnly);

    const isLoading = ref(false);

    function loadingState(loading: boolean) {
        isLoading.value = loading;
    }

    const onExpandSubflow = (expandedSubflows: string[]) => {
        flowStore.expandedSubflows = expandedSubflows;
    };

    const onSwappedTask = (swappedTasks: [string, string]) => {
        onExpandSubflow(expandedSubflows.value.map((expandedSubflow) => {
            let swappedTaskSplit;
            if (expandedSubflow === swappedTasks[0]) {
                swappedTaskSplit = swappedTasks[1].split(".");
                swappedTaskSplit.pop();

                return (
                    swappedTaskSplit.join(".") +
                    "." +
                    Utils.afterLastDot(expandedSubflow)
                );
            }
            if (expandedSubflow === swappedTasks[1]) {
                swappedTaskSplit = swappedTasks[0].split(".");
                swappedTaskSplit.pop();

                return (
                    swappedTaskSplit.join(".") +
                    "." +
                    Utils.afterLastDot(expandedSubflow)
                );
            }

            return expandedSubflow;
        }))
    };

    const onEdit = async (source: string, currentIsFlow = false) => {
        flowStore.flowYaml = source
        const result = await flowStore.onEdit({
            source,
            editorViewType: "YAML",
            topologyVisible: true,
        })
        
        if (currentIsFlow && source) {
            await flowStore.loadGraphFromSource({
                flow: source,
            }).catch((error) => {
                console.error("Error loading graph:", error);
            })
        }
        
        return result
    }
</script>

<style scoped>
    .vue-flow {
        height: 100%;
    }
    :deep(.vue-flow__panel.bottom) {
        bottom: 2rem !important;
    }
    .invalid-graph {
        margin: 1rem;
        width: auto;
    }
</style>