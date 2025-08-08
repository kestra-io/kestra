<template>
    <div id="topologyWrapper" v-loading="isLoading" class="vue-flow">
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
        />
        <div v-else-if="invalidGraph">
            <el-alert
                :title="t('topology-graph.invalid')"
                type="error"
                class="invalid-graph"
                :closable="false"
            >
                {{ t('topology-graph.invalid_description') }}
            </el-alert>
        </div>
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";
    import {Utils} from "@kestra-io/ui-libs";
    import LowCodeEditor from "./LowCodeEditor.vue";

    const store = useStore();
    const {t} = useI18n();

    const flowYaml = computed(() => store.state.flow.flowYaml);
    const flowGraph = computed(() => store.state.flow.flowGraph);
    const invalidGraph = computed(() => store.state.flow.invalidGraph);
    const flowId = computed(() => store.state.flow.id);
    const namespace = computed(() => store.state.flow.namespace);
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
