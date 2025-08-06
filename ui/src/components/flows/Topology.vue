<template>
    <el-card>
        <div class="vueflow">
            <LowCodeEditor
                v-if="flowStore.flow && flowStore.flowGraph"
                :flow-id="flowStore.flow.id"
                :namespace="flowStore.flow.namespace"
                :flow-graph="flowStore.flowGraph"
                :source="flowStore.flow.source"
                :is-read-only="isReadOnly"
                :expanded-subflows="expandedSubflows"
                @expand-subflow="onExpandSubflow($event)"
                @on-edit="(event) => emit('on-edit', event, true)"
            />
            <el-alert v-else type="warning" :closable="false">
                {{ $t("unable to generate graph") }}
            </el-alert>
        </div>
    </el-card>
</template>
<script>
    import {mapStores} from "pinia";
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";
    import {useFlowStore} from "../../stores/flow";

    export default {
        components: {
            LowCodeEditor,
        },
        emits: [
            "expand-subflow", "on-edit"
        ],
        props: {
            isReadOnly: {
                type: Boolean,
                default: false
            },
            expandedSubflows: {
                type: Array,
                default: () => []
            }
        },
        computed: {
            ...mapStores(useFlowStore),
        },
        beforeUnmount() {
            this.flowStore.flowValidation = undefined;
        },
        methods: {
            onExpandSubflow(event) {
                this.$emit("expand-subflow", event);
                this.flowStore.loadGraph({
                    flow: this.flowStore.flow,
                    params: {
                        subflows: event
                    }
                });
            }
        }
    };
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
