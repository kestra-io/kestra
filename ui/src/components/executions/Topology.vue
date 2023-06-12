<template>
    <el-card>
        <low-code-editor
            :key="execution.id"
            v-if="execution && flowGraph"
            :flow-id="execution.flowId"
            :namespace="execution.namespace"
            :flow-graph="flowGraph"
            :source="flow.source"
            :execution="execution"
            @follow="forwardEvent('follow', $event)"
        />
    </el-card>
</template>
<script>
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";
    import {mapState} from "vuex";
    export default {
        components: {
            LowCodeEditor
        },
        computed: {
            ...mapState("flow", ["flow","flowGraph"]),
            ...mapState("execution", ["execution"])
        },
        props: {
            preventRouteInfo: {
                type: Boolean,
                default: false
            }
        },
        data() {
            return {
                previousExecutionId: undefined
            };
        },
        watch: {
            execution() {
                this.loadGraph();
            },
        },
        mounted() {
            this.loadData();
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            loadData(){
                this.loadGraph();
            },
            loadGraph() {
                if (this.execution && (this.flowGraph === undefined || this.previousExecutionId !== this.execution.id)) {
                    this.previousExecutionId = this.execution.id;
                    this.$store.dispatch("flow/loadGraph", {
                        namespace: this.execution.namespace,
                        id: this.execution.flowId,
                        revision: this.execution.flowRevision
                    })
                }
            },
        }
    };
</script>
