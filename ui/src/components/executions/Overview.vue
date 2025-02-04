<template>
    <div v-if="execution" class="execution-overview">
        <div v-if="isFailed()">
            <el-alert type="error" :closable="false" class="mb-4 main-error">
                <template #title>
                    <div @click="isExpanded = !isExpanded">
                        <alert class="main-icon" />
                        {{ $t('execution failed header', errorLast ? 0 : 1, {message: errorLast?.message}) }}
                        <span v-if="errorLast" v-html="$t('execution failed message', {message: errorLast?.message})" />
                        <span class="toggle-icon" v-if="errorLogs">
                            <chevron-up v-if="isExpanded" />
                            <chevron-down v-else />
                        </span>
                    </div>
                </template>
                <div v-if="isExpanded && errorLogs" class="error-stack">
                    <div v-for="log in errorLogs" :key="log" class="stack-line">
                        <log-line :level="log.level" :log="log" :exclude-metas="['namespace', 'flowId', 'executionId']" />
                    </div>
                    <div class="text-end" v-if="errorLogsMore">
                        <router-link :to="{name: 'executions/update', params: {tenantId: execution.tenantId, id: execution.id, namespace: execution.namespace, flowId: execution.flowId, tab: 'logs'}, query: {level: 'ERROR'}}">
                            <el-button class="mt-3">
                                {{ $t('homeDashboard.errorLogs') }}
                            </el-button>
                        </router-link>
                    </div>
                </div>
            </el-alert>
        </div>

        <div v-if="isRestarted()">
            <el-alert type="warning" :closable="false" class="mb-4 main-warning">
                <template #title>
                    <div>
                        <alert class="main-icon" />
                        {{ $t('execution restarted', {nbRestart: execution?.metadata?.attemptNumber - 1}) }}
                    </div>
                </template>
            </el-alert>
        </div>

        <div v-if="isReplayed()">
            <el-alert type="info" :closable="false" class="mb-4 main-info">
                <template #title>
                    <div>
                        {{ $t('execution replayed') }}
                    </div>
                </template>
            </el-alert>
        </div>

        <div v-if="isReplay()">
            <el-alert type="info" :closable="false" class="mb-4 main-info">
                <template #title>
                    <div>
                        <span v-html="$t('execution replay', {originalId: execution?.originalId})" />
                    </div>
                </template>
            </el-alert>
        </div>

        <el-row class="mb-3">
            <el-col :span="12" class="crud-align">
                <crud type="CREATE" permission="EXECUTION" :detail="{executionId: execution.id}" />
            </el-col>
            <el-col :span="12" class="gap-2 d-flex justify-content-end actions-buttons">
                <set-labels :execution="execution" />
                <restart is-replay :execution="execution" @follow="forwardEvent('follow', $event)" />
                <restart :execution="execution" @follow="forwardEvent('follow', $event)" />
                <change-execution-status :execution="execution" @follow="forwardEvent('follow', $event)" />
                <resume :execution="execution" />
                <pause :execution="execution" />
                <kill :execution="execution" />
                <unqueue :execution="execution" />
                <force-run :execution="execution" />
                <status :status="execution.state.current" />
            </el-col>
        </el-row>

        <el-table table-layout="auto" fixed :data="items" :show-header="false" class="mb-0">
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
            <KestraCascader
                :options="transform({...execution.trigger, ...(execution.trigger.trigger ? execution.trigger.trigger : {})})"
                :execution
                class="overflow-auto"
            />
        </div>

        <div v-if="execution.inputs" class="my-5">
            <h5>{{ $t("inputs") }}</h5>
            <KestraCascader
                :options="transform(execution.inputs)"
                :execution
                class="overflow-auto"
            />
        </div>

        <div v-if="execution.variables" class="my-5">
            <h5>{{ $t("variables") }}</h5>
            <KestraCascader
                :options="transform(execution.variables)"
                :execution
                class="overflow-auto"
            />
        </div>

        <div v-if="execution.outputs" class="my-5">
            <h5>{{ $t("outputs") }}</h5>
            <KestraCascader
                :options="transform(execution.outputs)"
                :execution
                class="overflow-auto"
            />
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
    import Unqueue from "./Unqueue.vue";
    import ForceRun from "./ForceRun.vue";
    import Kill from "./Kill.vue";
    import {State} from "@kestra-io/ui-libs"
    import DateAgo from "../layout/DateAgo.vue";
    import Crud from "override/components/auth/Crud.vue";
    import Duration from "../layout/Duration.vue";
    import Labels from "../layout/Labels.vue"
    import {toRaw} from "vue";
    import ChangeExecutionStatus from "./ChangeExecutionStatus.vue";
    import KestraCascader from "../../components/kestra/Cascader.vue"
    import LogLine from "../../components/logs/LogLine.vue"
    import Alert from "vue-material-design-icons/Alert.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";

    export default {
        components: {
            ChangeExecutionStatus,
            Duration,
            Status,
            SetLabels,
            Restart,
            Resume,
            Pause,
            Unqueue,
            ForceRun,
            Kill,
            DateAgo,
            Labels,
            Crud,
            KestraCascader,
            LogLine,
            Alert,
            ChevronDown,
            ChevronUp
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
            },
            isFailed() {
                return this.execution.state.current === State.FAILED;
            },
            isRestarted() {
                return this.execution.labels?.find( it => it.key === "system.restarted" && (it.value === "true" || it.value === true)) !== undefined;
            },
            isReplayed() {
                return this.execution.labels?.find( it => it.key === "system.replayed" && (it.value === "true" || it.value === true)) !== undefined;
            },
            isReplay() {
                return this.execution.labels?.find( it => it.key === "system.replay" && (it.value === "true" || it.value === true)) !== undefined;
            },
            load() {
                this.$store
                    .dispatch(
                        "execution/loadExecution",
                        this.$route.params
                    )
                    .then(() => {
                        this.fetchErrorLogs();
                    })
            },
            fetchErrorLogs() {
                this.$store
                    .dispatch("execution/loadLogs", {
                        store: false,
                        executionId: this.execution.id,
                        params: {
                            minLevel: "ERROR"
                        }
                    })
                    .then(response => {
                        if (response && response.length >= 1) {
                            this.errorLogsMore = response.length > 3;
                            this.errorLast = response[response.length - 1];
                            this.errorLogs = response.length > 3 ? response.slice(1).slice(-3) : response;

                        } else {
                            this.errorLogs = undefined;
                            this.errorLogsMore = false;
                            this.errorLast = undefined;
                        }
                    })
            }
        },
        mounted() {
            if (this.isFailed()) {
                this.fetchErrorLogs();
            }
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && this.execution.id !== this.$route.params.id) {
                    this.load();
                }
            }
        },
        data() {
            return {
                isExpanded: false,
                errorLogs: undefined,
                errorLogsMore: false,
                errorLast: undefined,
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

.execution-overview {
    .cascader {
        &::-webkit-scrollbar {
            height: 5px;
        }

        &::-webkit-scrollbar-track {
            background: var(--ks-background-card);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--ks-button-background-primary);
            border-radius: 0px;
        }
    }

    .wrapper {
        background: var(--ks-background-card);
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
            color: var(--ks-content-primary);
            padding: 0 30px 0 5px;

            &[aria-haspopup="false"] {
                padding-right: 0.5rem !important;
            }

            &:hover {
                background-color: var(--ks-border-primary);
            }

            &.in-active-path,
            &.is-active {
                background-color: var(--ks-border-primary);
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
                color: var(--ks-content-primary);
            }
        }
    }

    .actions-buttons {
        .el-button {
            margin-left: 0 !important;
            margin-right: 0 !important;
        }
    }
}

.el-alert.main-error {
    background-color: var(--ks-background-error) !important;
    padding: 0.5rem;

    .el-button{
        color: var(--ks-log-content-error);
        background-color: var(--ks-log-background-error);
        border-color: var(--ks-log-border-error);
    }
    .el-alert__title {
        cursor: pointer;
        font-weight: bold;
        position: relative;
        line-height: 2rem;
        color: var(--ks-content-error) !important;
        font-size: var(--font-size-sm);

        span {
            font-weight: normal;
        }

        code{
            color: var(--ks-log-content-error) !important;
        }

        > div {
            padding-right: 3rem;
        }

        .main-icon.material-design-icon  {
            color: var(--ks-content-alert);
            font-size: 1.25rem;
            position: relative;
            top: 4px;
            margin-right: 0.75rem;
        }

        .toggle-icon {
            position: absolute;
            color: var(--ks-content-alert);
            right: 1rem;
            width: 1rem;
            height: 1rem;
            font-size: 1.75rem;
            top: 10%;
        }

    }

    .el-alert__description {
        color: var(--ks-content-primary);
    }

    .el-alert__content {
        width: 100%;

        .error-stack {
            margin-top: 0.5rem;
        }

        .text-end {
            border-top: 1px solid var(--ks-log-background-error);
        }
    }
}

.stack-line {
    margin-bottom: 0;

    .line {
        padding: .5rem;
        border-top: 1px solid var(--ks-log-background-error);
    }
}
</style>