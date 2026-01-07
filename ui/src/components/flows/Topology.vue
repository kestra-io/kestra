<template>
    <el-card>
        <div class="vueflow">
            <LowCodeEditor
                v-if="flowStore.flow && flowStore.flowGraph"
                :flowId="flowStore.flow.id"
                :namespace="flowStore.flow.namespace"
                :flowGraph="flowStore.flowGraph"
                :source="flowStore.flow.source"
                :isReadOnly="isReadOnly"
                :expandedSubflows="expandedSubflows"
                @expand-subflow="onExpandSubflow"
                @on-edit="(event) => emit('on-edit', event, true)"
            />
            <el-alert v-else type="warning" :closable="false">
                {{ $t("unable to generate graph") }}
            </el-alert>
        </div>
    </el-card>
</template>
<script setup lang="ts">
    import {onBeforeUnmount} from "vue";
    import {useFlowStore} from "../../stores/flow";
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";

    defineProps<{
        isReadOnly?: boolean;
        expandedSubflows?: any[];
    }>();

    const emit = defineEmits<{
        (e: "expand-subflow", event: any): void;
        (e: "on-edit", event: any, flag: boolean): void;
    }>();

    const flowStore = useFlowStore();

    function onExpandSubflow(event: any) {
        emit("expand-subflow", event);
        if(flowStore.flow){
            flowStore.loadGraph({
                flow: flowStore.flow,
                params: {
                    subflows: event
                }
            });
        }
    }

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined;
    });
</script>
<style scoped lang="scss">
    .el-card {
        height: calc(100vh - 174px);
        position: relative;

        :deep(.el-card__body) {
            height: 100%;
            display: flex;
        }
    }

    .vueflow {
        height: 100%;
        width: 100%;
    }
</style>
