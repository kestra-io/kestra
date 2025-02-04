<template>
    <div class="taskrun-header">
        <div>
            <el-icon
                v-if="!taskRunId && shouldDisplayChevron(currentTaskRun)"
                type="default"
                @click.stop="() => forwardEvent('toggleShowAttempt',(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])))"
            >
                <ChevronDown
                    v-if="shownAttemptsUid.includes(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                />
                <ChevronRight v-else />
            </el-icon>
        </div>
        <div class="task-icon d-none d-md-inline-block me-1">
            <task-icon
                :cls="taskType(currentTaskRun)"
                v-if="taskType(currentTaskRun)"
                only-icon
                :icons="icons"
            />
        </div>

        <div
            class="task-id flex-grow-1"
            :id="`attempt-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${currentTaskRun.id}`"
        >
            <el-tooltip :persistent="false" transition="" :hide-after="0" effect="light">
                <template #content>
                    {{ $t("from") }} :
                    {{ $filters.date(selectedAttempt(currentTaskRun).state.startDate) }}
                    <br>
                    {{ $t("to") }} :
                    {{ $filters.date(selectedAttempt(currentTaskRun).state.endDate) }}
                    <br>
                    <clock />
                    <strong>{{ $t("duration") }}:</strong>
                    {{ $filters.humanizeDuration(selectedAttempt(currentTaskRun).state.duration) }}
                </template>
                <span>
                    <span class="me-1 fw-bold">{{ currentTaskRun.taskId }}</span>
                    <small v-if="currentTaskRun.value">
                        {{ currentTaskRun.value }}
                    </small>
                </span>
            </el-tooltip>
        </div>

        <div class="task-duration d-none d-md-inline-block">
            <small class="me-1">
                <duration :histories="currentTaskRun.state.histories" />
            </small>
        </div>

        <div class="task-status">
            <status size="small" :status="currentTaskRun.state.current" />
        </div>

        <slot name="buttons" />

        <el-dropdown trigger="click">
            <el-button type="default" class="task-run-buttons">
                <DotsVertical title="" />
            </el-button>
            <template #dropdown>
                <el-dropdown-menu>
                    <sub-flow-link
                        v-if="isSubflow(currentTaskRun)"
                        component="el-dropdown-item"
                        tab-execution="logs"
                        :execution-id="currentTaskRun.outputs.executionId"
                    />

                    <metrics :task-run="currentTaskRun" :execution="followedExecution" />

                    <outputs
                        :outputs="currentTaskRun.outputs"
                        :execution="followedExecution"
                    />

                    <restart
                        component="el-dropdown-item"
                        :key="`restart-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        is-replay
                        tooltip-position="left"
                        :execution="followedExecution"
                        :task-run="currentTaskRun"
                        :attempt-index="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="forwardEvent('follow', $event)"
                    />

                    <change-status
                        component="el-dropdown-item"
                        :key="`change-status-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        :execution="followedExecution"
                        :task-run="currentTaskRun"
                        :attempt-index="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="forwardEvent('follow', $event)"
                    />
                    <task-edit
                        v-if="canReadFlow"
                        :read-only="true"
                        component="el-dropdown-item"
                        :task-id="currentTaskRun.taskId"
                        :section="SECTIONS.TASKS"
                        :flow-id="followedExecution.flowId"
                        :namespace="followedExecution.namespace"
                        :revision="followedExecution.flowRevision"
                        :flow-source="flow?.source"
                    />
                    <el-dropdown-item
                        :icon="Download"
                        @click="downloadContent(currentTaskRun.id)"
                    >
                        {{ $t("download logs") }}
                    </el-dropdown-item>
                    <el-dropdown-item
                        :icon="Delete"
                        @click="deleteLogs(currentTaskRun.id)"
                    >
                        {{ $t("delete logs") }}
                    </el-dropdown-item>
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </div>
    <div class="attempt-header">
        <el-select
            class="d-none d-md-inline-block attempt-select"
            :model-value="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
            @change="forwardEvent('swapDisplayedAttempt', {taskRunId: currentTaskRun.id, attemptNumber: $event})"
            :disabled="!currentTaskRun.attempts || currentTaskRun.attempts?.length <= 1"
        >
            <el-option
                v-for="(_, index) in attempts(currentTaskRun)"
                :key="`attempt-${index}-${currentTaskRun.id}`"
                :value="index"
                :label="`${$t('attempt')} ${index + 1}`"
            />
        </el-select>

        <div class="task-status">
            <status size="small" :status="selectedAttempt(currentTaskRun).state.current" />
        </div>

        <div class="task-duration d-none d-md-inline-block">
            <small class="me-1">
                <duration :histories="selectedAttempt(currentTaskRun).state.histories" />
            </small>
        </div>
    </div>
</template>
<script>
    import Restart from "./Restart.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import Metrics from "./Metrics.vue";
    import Status from "../Status.vue";
    import ChangeStatus from "./ChangeStatus.vue";
    import TaskEdit from "../flows/TaskEdit.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import Outputs from "./Outputs.vue";
    import {State} from "@kestra-io/ui-libs"
    import FlowUtils from "../../utils/flowUtils";
    import {mapState} from "vuex";
    import {SECTIONS} from "../../utils/constants";
    import Download from "vue-material-design-icons/Download.vue";
    import _groupBy from "lodash/groupBy";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import Duration from "../layout/Duration.vue";
    import Utils from "../../utils/utils";
    import Delete from "vue-material-design-icons/Delete.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";

    export default {
        components: {
            TaskIcon,
            Outputs,
            Clock,
            ChevronDown,
            DotsVertical,
            SubFlowLink,
            TaskEdit,
            ChangeStatus,
            Status,
            Metrics,
            ChevronRight,
            Restart,
            Duration
        },
        props: {
            currentTaskRun: {
                type: Object,
                required: true
            },
            followedExecution: {
                type: Object,
                required: true
            },
            flow: {
                type: Object,
                default: undefined
            },
            forcedAttemptNumber: {
                type: Number,
                default: undefined
            },
            taskRunId: {
                type: String,
                default: undefined,
            },
            selectedAttemptNumberByTaskRunId: {
                type: Object,
                default: () => ({}),
            },
            shownAttemptsUid: {
                type: Array,
                default: () => [],
            },
            logs: {
                type: Array,
                default: () => [],
            },
            filter: {
                type: String,
                default: ""
            }
        },
        computed: {
            Delete() {
                return Delete
            },
            Download() {
                return Download
            },
            ...mapState("plugin", ["icons"]),
            ...mapState("auth", ["user"]),
            SECTIONS() {
                return SECTIONS
            },
            currentTaskRuns() {
                return this.followedExecution?.taskRunList?.filter(tr => this.taskRunId ? tr.id === this.taskRunId : true) ?? [];
            },
            taskRunById() {
                return Object.fromEntries(this.currentTaskRuns.map(taskRun => [taskRun.id, taskRun]));
            },
            logsWithIndexByAttemptUid() {
                const indexedLogs = this?.logs
                    .filter(logLine => (logLine?.message ?? "").toLowerCase().includes(this.filter) || this.isSubflow(this.taskRunById[logLine.taskRunId]))
                    .map((logLine, index) => ({...logLine, index}));

                return _groupBy(indexedLogs, indexedLog => this.attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber));
            },
            canReadFlow() {
                return this.user.isAllowed(permission.FLOW, action.READ, this.$route.params.namespace)
            }
        },
        methods: {
            attempts(taskRun) {
                if (this.followedExecution.state.current === State.RUNNING || this.forcedAttemptNumber === undefined) {
                    return taskRun.attempts ?? [{state: taskRun.state}];
                }

                return taskRun.attempts ? [taskRun.attempts[this.forcedAttemptNumber]] : [];
            },
            isSubflow(taskRun) {
                return taskRun.outputs?.executionId;
            },
            downloadName(currentTaskRunId) {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.followedExecution.id}-${currentTaskRunId}.log`
            },
            selectedAttempt(taskRun) {
                return this.attempts(taskRun)[this.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0];
            },
            taskType(taskRun) {
                if(!taskRun) return undefined;

                const task = FlowUtils.findTaskById(this.flow, taskRun.taskId);
                const parentTaskRunId = taskRun.parentTaskRunId;
                if (task === undefined && parentTaskRunId) {
                    return this.taskType(this.taskRunById[parentTaskRunId])
                }
                return task ? task.type : undefined;
            },
            downloadContent(currentTaskRunId) {
                const params = this.params
                this.$store.dispatch("execution/downloadLogs", {
                    executionId: this.followedExecution.id,
                    params: {...params, taskRunId: currentTaskRunId}
                }).then((response) => {
                    Utils.downloadUrl(window.URL.createObjectURL(new Blob([response])), this.downloadName(currentTaskRunId));
                });
            },
            deleteLogs(currentTaskRunId) {
                const params = this.params
                this.$toast().confirm(
                    this.$t("delete_log"),
                    () => {
                        this.$store.dispatch("execution/deleteLogs", {
                            executionId: this.followedExecution.id,
                            params: {...params, taskRunId: currentTaskRunId}
                        }).then((_) => {
                            this.forwardEvent("update-logs", this.followedExecution.id)
                        });
                    },
                    () => {}
                )

            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            attemptUid(taskRunId, attemptNumber) {
                return `${taskRunId}-${attemptNumber}`
            },
            shouldDisplayChevron(taskRun) {
                return this.shouldDisplayProgressBar(taskRun) || this.shouldDisplayLogs(taskRun.id)
            },
            shouldDisplayProgressBar(taskRun) {
                return this.taskType(taskRun) === "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable" || this.taskType(taskRun) === "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable"
            },
            shouldDisplayLogs(taskRunId) {
                return this.logsWithIndexByAttemptUid[this.attemptUid(taskRunId, this.selectedAttemptNumberByTaskRunId[taskRunId])]
            }
        }
    }
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .task-duration {
        padding: .375rem 0;
    }

    .taskrun-header, .attempt-header {
        display: flex;
        gap: .5rem;
        padding: 0.5rem 1rem;
        border-bottom: 1px solid var(--ks-border-primary);

        > * {
            display: flex;
            align-items: center;
        }

        small {
            font-family: var(--bs-font-monospace);
            font-size: var(--font-size-xs)
        }

        .task-duration small {
            white-space: nowrap;
            color: var(--ks-content-secondary);
        }

    }

    .taskrun-header {
        background-color: var(--ks-background-table-header);
        .task-icon {
            width: 36px;
            padding: 6px 6px 6px 0;
            border-radius: $border-radius-lg;
            margin-left: -0.5rem;
        }

        .task-id {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            padding: .375rem 0;

            span span {
                color: var(--ks-content-primary);
                font-size: 14px;

                html:not(.dark) & {
                    color: $black;
                }
            }
        }

        .task-run-buttons {
            padding: 0 .5rem;
            border: 1px solid rgba($white, .05);
            background-color: var(--ks-button-background-secondary) !important;
            // FIXME: what does this mean?
            &:not(:hover) {
                background: rgba($white, .10);
            }
        }
    }

    .attempt-header {
        .el-select {
            width: 10rem;
            height: 24px;
            margin-top: 0.35rem;

            :deep(.el-select__wrapper) {
                height: 24px;
                min-height: 24px;
            }

        }

        .attempt-number {
            background: var(--bs-gray-400);
            padding: .375rem .75rem;
            white-space: nowrap;
        }
    }
</style>

<style lang="scss">
.attempt-select > .el-select__wrapper {
    height: 100%;
}
</style>