<template>
    <el-card>
        <div class="vueflow">
            <LowCodeEditor
                :key="execution.id"
                v-if="execution && flowGraph"
                :flowId="execution.flowId"
                :namespace="execution.namespace"
                :flowGraph="flowGraph"
                :source="flowStore.flow?.source"
                :execution="execution"
                :expandedSubflows="expandedSubflows"
                isReadOnly
                @follow="$emit('follow', $event)"
                viewType="topology"
                @expand-subflow="onExpandSubflow"
            />
            <el-loading v-else-if="loading" />
            <el-alert v-else type="warning" :closable="false">
                {{ $t("unable to generate graph") }}
            </el-alert>
        </div>
    </el-card>
</template>
<script>
    import throttle from "lodash/throttle";
    import {mapStores} from "pinia";
    import {Utils, State} from "@kestra-io/ui-libs";
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";
    import {useExecutionsStore} from "../../stores/executions";
    import {useFlowStore} from "../../stores/flow";
    export default {
        emits: ["follow"],
        components: {
            LowCodeEditor
        },
        computed: {
            ...mapStores(useExecutionsStore, useFlowStore),
            execution() {
                return this.executionsStore.execution;
            },
            flowGraph() {
                return this.executionsStore.flowGraph;
            }
        },
        data() {
            return {
                loading: true,
                previousExecutionId: undefined,
                expandedSubflows: [],
                previousExpandedSubflows: [],
                sseBySubflow: {},
                throttledExecutionUpdate: throttle(function (subflow, executionEvent) {
                    const previousExecution = this.executionsStore.subflowsExecutions[subflow];
                    this.executionsStore.addSubflowExecution({
                        subflow,
                        execution: JSON.parse(executionEvent.data)
                    });

                    // add subflow execution id to graph
                    if(previousExecution === undefined) {
                        this.loadGraph(true);
                    }
                }, 500)
            };
        },
        watch: {
            execution() {
                this.loadData();
            }
        },
        mounted() {
            this.loadData();
        },
        unmounted() {
            Object.keys(this.sseBySubflow).forEach(this.closeSSE);
        },
        methods: {
            closeSSE(subflow) {
                this.sseBySubflow[subflow].close();
                delete this.sseBySubflow[subflow];
                this.executionsStore.removeSubflowExecution(subflow)
            },
            loadData() {
                this.loadGraph();
            },
            loadGraph(force) {
                this.loading = true;

                if (this.execution && (force || (this.flowGraph === undefined || this.previousExecutionId !== this.execution.id))) {
                    this.previousExecutionId = this.execution.id;
                    this.executionsStore.loadAugmentedGraph({
                        id: this.execution.id,
                        params: {
                            subflows: this.expandedSubflows
                        }
                    }).catch(() => {
                        this.expandedSubflows = this.previousExpandedSubflows;

                        this.handleSubflowsSSE();
                    }).finally(() => {
                        this.loading = false;
                    });
                } else {
                    this.loading = false;
                }
            },
            onExpandSubflow(expandedSubflows) {
                this.previousExpandedSubflows = this.expandedSubflows;
                this.expandedSubflows = expandedSubflows;

                this.handleSubflowsSSE();
            },
            handleSubflowsSSE() {
                Object.keys(this.sseBySubflow).filter(subflow => !this.expandedSubflows.includes(subflow))
                    .forEach(this.closeSSE);

                // resolve parent subflows' execution first
                const subflowsWithoutSSE = this.expandedSubflows.filter(subflow => !(subflow in this.sseBySubflow))
                    .sort((a, b) => (a.match(/\./g)?.length || 0) - (b.match(/\./g)?.length || 0));


                subflowsWithoutSSE.forEach(subflow => {
                    this.addSSE(subflow, true);
                });
            },
            delaySSE(generateGraphBeforeDelay, subflow) {
                if(generateGraphBeforeDelay) {
                    this.loadGraph(true);
                }
                setTimeout(() => this.addSSE(subflow), 500);
            },
            addSSE(subflow, generateGraphOnWaiting) {
                let parentExecution = this.execution;

                const parentSubflows = this.expandedSubflows.filter(expandedSubflow => subflow.includes(expandedSubflow + "."))
                    .sort((s1, s2) => s2.length - s1.length);

                if(parentSubflows.length > 0) {
                    parentExecution = this.executionsStore.subflowsExecutions[parentSubflows[0]];
                }

                if(!parentExecution) {
                    this.delaySSE(generateGraphOnWaiting, subflow);
                    return;
                }

                const taskIdMatchingTaskrun = parentExecution.taskRunList
                    .filter(taskRun => taskRun.taskId === Utils.afterLastDot(subflow))?.[0];
                const executionId = taskIdMatchingTaskrun?.outputs?.executionId;

                if(!executionId) {
                    if(taskIdMatchingTaskrun?.state?.current === State.SUCCESS) {
                        // Generating more than 1 subflow execution, we're not showing anything
                        this.loadGraph(true);
                        return;
                    }

                    this.delaySSE(generateGraphOnWaiting, subflow);
                    return;
                }

                this.executionsStore.followExecution({id: executionId}, this.$t)
                    .then(sse => {
                        this.sseBySubflow[subflow] = sse;
                        sse.onmessage = (executionEvent) => {
                            const isEnd = executionEvent && executionEvent.lastEventId === "end";
                            if (isEnd) {
                                this.closeSubExecutionSSE(subflow);
                            }
                            // we are receiving a first "fake" event to force initializing the connection: ignoring it
                            if (executionEvent.lastEventId !== "start") {
                                this.throttledExecutionUpdate(subflow, executionEvent);
                            }
                            if (isEnd) {
                                this.throttledExecutionUpdate.flush();
                            }
                        };
                    });
            },
            closeSubExecutionSSE(subflow) {
                const sse = this.sseBySubflow[subflow];
                if (sse) {
                    sse.close();
                    delete this.sseBySubflow[subflow];
                }
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
