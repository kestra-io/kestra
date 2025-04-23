<template>
    <div v-loading="isLoading" class="vue-flow">
        <LowCodeEditor
            v-if="flowGraph"
            :flow-graph="flowGraph"
            :flow-id="flowId"
            :namespace="namespace"
            :is-read-only="isReadOnly"
            :source="flowYaml"
            :is-allowed-edit="isAllowedEdit"
            :expanded-subflows="expandedSubflows"
            @on-edit="onEdit"
            @loading="loadingState"
            @expand-subflow="onExpandSubflow"
            @swapped-task="onSwappedTask"
            @open-no-code="handleTopologyEditClick"
        />
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref} from "vue";
    import {useStore} from "vuex";
    import {useRouter, useRoute} from "vue-router";
    import {Utils} from "@kestra-io/ui-libs";
    import LowCodeEditor from "./LowCodeEditor.vue";

    const store = useStore();
    const router = useRouter();
    const route = useRoute();

    const flowYaml = computed(() => store.getters["flow/flowYaml"]);
    const flowGraph = computed(() => store.state.flow.flowGraph);
    const flowId = computed(() => store.getters["flow/id"]);
    const namespace = computed(() => store.getters["flow/namespace"]);
    const expandedSubflows = computed<string[]>(() => store.state.flow.expandedSubflows);
    const isAllowedEdit = computed(() => store.getters["flow/isAllowedEdit"]);
    const isReadOnly = computed(() => store.getters["flow/isReadOnly"]);

    const isLoading = ref(false);

    function loadingState(loading: boolean) {
        isLoading.value = loading;
    }

    const onExpandSubflow = (expandedSubflows: string[]) => {
        store.commit("flow/setExpandedSubflows", expandedSubflows);
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

    const onEdit = (source:string, currentIsFlow = false) => {
        store.commit("flow/setFlowYaml", source)
        return store.dispatch("flow/onEdit", {
            source,
            currentIsFlow,
            editorViewType: "YAML",
        })
    }

    const handleTopologyEditClick = (params: any) => {
        router.replace({query: {
            ...route.query,
            ...params
        }})
    }
</script>

<style scoped>
    .vue-flow {
        height: 100%;
    }
    :deep(.vue-flow__panel.bottom) {
        bottom: 2rem !important;
    }
</style>
