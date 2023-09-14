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
        props: {
            preventRouteInfo: {
                type: Boolean,
                default: false
            },
            isReadOnly: {
                type: Boolean,
                default: false
            },
            expandedSubflows: {
                type: Array,
                default: []
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
        height: calc(100vh - 300px);
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
