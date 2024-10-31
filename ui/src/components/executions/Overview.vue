<template>
    <div v-if="execution" class="execution-overview">
        <div v-if="execution.state.current === 'FAILED'" class="error-container">
            <div class="error-header" @click="isExpanded = !isExpanded">
                <svg xmlns="http://www.w3.org/2000/svg" class="error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                    <line x1="12" y1="9" x2="12" y2="13" />
                    <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
                <span class="error-message">{{ errorMessage }}</span>
                <span class="toggle-icon">
                    <svg v-if="isExpanded" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="arrow-icon">
                        <path d="M18 15l-6-6-6 6" />
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="arrow-icon">
                        <path d="M6 9l6 6 6-6" />
                    </svg>
                </span>
            </div>
            <div v-if="isExpanded" class="error-stack">
                <div v-for="(line, index) in stackTrace.split('\n')" :key="index" class="stack-line">
                    {{ line }}
                </div>
            </div>
        </div>

        <el-row class="mb-3">
            <el-col :span="12" class="crud-align">
                <crud type="CREATE" permission="EXECUTION" :detail="{executionId: execution.id}" />
            </el-col>
            <el-col :span="12" class="d-flex gap-2 justify-content-end">
                <set-labels :execution="execution" />
                <restart is-replay :execution="execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
                <restart :execution="execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
                <change-execution-status :execution="execution" @follow="forwardEvent('follow', $event)" />
                <resume :execution="execution" />
                <pause :execution="execution" />
                <kill :execution="execution" class="ms-0" />
                <status :status="execution.state.current" class="ms-0" />
            </el-col>
        </el-row>

        <el-table stripe table-layout="auto" fixed :data="items" :show-header="false" class="mb-0">
            <el-table-column prop="key" :label="$t('key')" />

            <el-table-column prop="value" :label="$t('value')">
                <template #default="scope">
                    <router-link
                        v-if="scope.row.link"
                        :to="{name: 'executions/update', params: scope.row.link}"
                    >
                        {{ scope.row.value }}
                    </router-link>
                    <span v-else-if="scope.row.date">
                        <date-ago :date="scope.row.value" />
                    </span>
                    <span v-else-if="scope.row.duration">
                        <duration :histories="scope.row.value" />
                    </span>
                    <span v-else-if="scope.row.key === $t('labels')">
                        <labels :labels="scope.row.value" :filter-enabled="false" />
                    </span>
                    <span v-else>
                        <span v-if="scope.row.key === $t('revision')">
                            <router-link
                                :to="{name: 'flows/update', params: {id: $route.params.flowId, namespace: $route.params.namespace, tab: 'revisions'}, query: {revisionRight: scope.row.value}}"
                            >{{ scope.row.value }}</router-link>
                        </span>
                        <span v-else>{{ scope.row.value }}</span>
                    </span>
                </template>
            </el-table-column>
        </el-table>

        <div v-if="execution.trigger" class="my-5">
            <h5>{{ $t("trigger") }}</h5>
            <KestraCascader :options="transform({...execution.trigger, ...(execution.trigger.trigger ? execution.trigger.trigger : {})})" class="overflow-auto" />
        </div>

        <div v-if="execution.inputs" class="my-5">
            <h5>{{ $t("inputs") }}</h5>
            <KestraCascader :options="transform(execution.inputs)" class="overflow-auto" />
        </div>

        <div v-if="execution.variables" class="my-5">
            <h5>{{ $t("variables") }}</h5>
            <KestraCascader :options="transform(execution.variables)" class="overflow-auto" />
        </div>

        <div v-if="execution.outputs" class="my-5">
            <h5>{{ $t("outputs") }}</h5>
            <KestraCascader :options="transform(execution.outputs)" class="overflow-auto" />
        </div>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import Status from "../Status.vue";
    import SetLabels from "./SetLabels.vue";
    import Restart from "./Restart.vue";
    import Resume from "./Resume.vue";
    import Pause from "./Pause.vue";
    import Kill from "./Kill.vue";
    import State from "../../utils/state";
    import DateAgo from "../layout/DateAgo.vue";
    import Crud from "override/components/auth/Crud.vue";
    import Duration from "../layout/Duration.vue";
    import Labels from "../layout/Labels.vue"
    import {toRaw} from "vue";
    import ChangeExecutionStatus from "./ChangeExecutionStatus.vue";
    import KestraCascader from "../../components/kestra/Cascader.vue"

    export default {
        components: {
            ChangeExecutionStatus,
            Duration,
            Status,
            SetLabels,
            Restart,
            Resume,
            Pause,
            Kill,
            DateAgo,
            Labels,
            Crud,
            KestraCascader
        },
        emits: ["follow"],
        methods: {
            transform(obj) {
                return Object.entries(obj).map(([key, value]) => {
                    const children =
                        typeof value === "object" && value !== null
                            ? Object.entries(value).map(
                                ([k, v]) => this.transform({[k]: v})[0],
                            )
                            : [{label: value, value: value}];

                    // Filter out children with undefined label and value
                    const filteredChildren = children.filter(
                        (child) =>
                            child.label !== undefined || child.value !== undefined,
                    );

                    // Return node with or without children based on existence
                    const node = {label: key, value: key};

                    // Include children only if there are valid entries
                    if (filteredChildren.length) {
                        node.children = filteredChildren;
                    }

                    return node;
                });
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            stop() {
                if (!this.execution || State.isRunning(this.execution.state.current)) {
                    return new Date().toISOString(true)
                } else {
                    return this.execution.state.histories[this.execution.state.histories.length - 1].date;
                }
            }
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && this.execution.id !== this.$route.params.id) {
                    this.$store.dispatch(
                        "execution/loadExecution",
                        this.$route.params
                    );
                }
            },
            execution: {
                handler(newExecution) {
                    if (newExecution?.error) {
                        this.errorMessage = newExecution.error.message || "";
                        this.stackTrace = newExecution.error.stacktrace || [];
                    }
                    else {
                        this.errorMessage = "";
                        this.stackTrace = [];
                    }
                },
                immediate: true
            }
        },
        data() {
            return {
                isExpanded: false
            };
        },
        computed: {
            ...mapState("execution", ["flow", "execution"]),
            items() {
                if (!this.execution) {
                    return []
                }
                const stepCount = this.execution.taskRunList
                    ? this.execution.taskRunList.length
                    : 0;
                let ret = [
                    {key: this.$t("namespace"), value: this.execution.namespace},
                    {key: this.$t("flow"), value: this.execution.flowId},
                    {
                        key: this.$t("revision"),
                        value: this.execution.flowRevision
                    },
                    {key: this.$t("labels"), value: this.execution.labels},
                    {key: this.$t("created date"), value: this.execution.state.histories[0].date, date: true},
                    {key: this.$t("updated date"), value: this.stop(), date: true},
                    {key: this.$t("duration"), value: this.execution.state.histories, duration: true},
                    {key: this.$t("steps"), value: stepCount},
                    {key: this.$t("attempt"), value: this.execution?.metadata?.attemptNumber},
                    {key: this.$t("originalCreatedDate"), value: this.execution?.metadata?.originalCreatedDate, date: true},
                    {key: this.$t("scheduleDate"), value: this.execution?.scheduleDate, date: true},
                ];

                if (this.execution.parentId) {
                    ret.push({
                        key: this.$t("parent execution"),
                        value: this.execution.parentId,
                        link: {
                            flowId: this.execution.flowId,
                            id: this.execution.parentId,
                            namespace: this.execution.namespace
                        }
                    });
                }

                if (this.execution.originalId && this.execution.originalId !== this.execution.id) {
                    ret.push({
                        key: this.$t("original execution"),
                        value: this.execution.originalId,
                        link: {
                            flowId: this.execution.flowId,
                            id: this.execution.originalId,
                            namespace: this.execution.namespace
                        }
                    });
                }

                return ret;
            },
            inputs() {
                if (!this.flow) {
                    return []
                }

                let inputs = toRaw(this.execution.inputs);
                Object.keys(inputs).forEach(key => {
                    (this.flow.inputs || []).forEach(input => {
                        if (key === input.name && input.type === "SECRET") {
                            inputs[key] = "******";
                        }
                    })
                })
                return inputs;
            }
        },
    };
</script>

<style lang="scss">
.crud-align {
    display: flex;
    align-items: center;
}

.error-container {
    background-color:var(--bs-border-color);
    border: 1px solid #ff6b6b;
    border-radius: 4px;
    color: #ffffff;
    margin: 10px 0 30px 0;
}

.error-header {
    background-color: var(--bs-body-bg);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 20px;
}

.error-icon {
    width: 24px;
    height: 24px;
    margin-right: 10px;
    color: #ff6b6b;
}

.error-message {
    font-weight: bold;
    flex-grow: 1;
    color: var(--el-text-color-regular);
}

.toggle-icon {
    font-size: 1.2em;
    color: #ff6b6b;
    display: flex;
    align-items: center;
}

.arrow-icon {
    width: 20px;
    height: 20px;
    cursor: pointer;
}

.error-stack {
    background-color: var(--bs-body-bg);
    border-radius: 4px;
    padding: 10px;
    overflow-x: auto; 
}

.stack-line {
    font-size: 0.9em;
    margin-bottom: 5px;
    color: var(--el-text-color-regular);
}

.execution-overview {
    .cascader {
        &::-webkit-scrollbar {
            height: 5px;
        }

        &::-webkit-scrollbar-track {
            background: var(--card-bg);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--bs-primary);
            border-radius: 0px;
        }
    }

    .wrapper {
        background: var(--card-bg);
    }

    .el-cascader-menu {
        min-width: 300px;
        max-width: 300px;

        .el-cascader-menu__list {
            padding: 0;
        }

        .el-cascader-menu__wrap {
            height: 100%;
        }

        & .el-cascader-node {
            height: 36px;
            line-height: 36px;
            font-size: var(--el-font-size-small);
            color: var(--el-text-color-regular);
            padding: 0 30px 0 5px;

            &[aria-haspopup="false"] {
                padding-right: 0.5rem !important;
            }

            &:hover {
                background-color: var(--bs-border-color);
            }

            &.in-active-path,
            &.is-active {
                background-color: var(--bs-border-color);
                font-weight: normal;
            }

            .el-cascader-node__prefix {
                display: none;
            }

            .task .wrapper {
                align-self: center;
                height: var(--el-font-size-small);
                width: var(--el-font-size-small);
            }

            code span.regular {
                color: var(--el-text-color-regular);
            }
        }
    }
}
</style>