<template>
    <el-card>
        <div class="vueflow">
            <LowCodeEditor
                v-if="flow && flowGraph"
                :flow-id="flow.id"
                :namespace="flow.namespace"
                :flow-graph="flowGraph"
                :source="flow.source"
                :is-read-only="isReadOnly"
                :expanded-subflows="expandedSubflows"
                view-type="topology"
                @expand-subflow="onExpandSubflow($event)"
            />
            <el-alert v-else type="warning" :closable="false">
                {{ $t("unable to generate graph") }}
            </el-alert>
        </div>
    </el-card>
</template>
<script>
    import {mapState} from "vuex";
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";

    export default {
        components: {
            LowCodeEditor,
        },
        emits: [
            "expand-subflow"
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
            ...mapState("flow", ["flow", "flowGraph"]),
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowError", undefined);
        },
        methods: {
            onExpandSubflow(event) {
                this.$emit("expand-subflow", event);
                this.$store.dispatch("flow/loadGraph", {
                    flow: this.flow,
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
