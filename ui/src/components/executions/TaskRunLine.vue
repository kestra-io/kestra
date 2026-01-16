<template>
    <div class="taskrun-header">
        <div>
            <el-icon
                v-if="!taskRunId && shouldDisplayChevron(currentTaskRun)"
                type="default"
                @click.stop="() => $emit('toggleShowAttempt',(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])))"
            >
                <ChevronDown
                    v-if="shownAttemptsUid.includes(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                />
                <ChevronRight v-else />
            </el-icon>
        </div>
        <div class="task-icon d-none d-md-inline-block me-1">
            <TaskIcon
                :cls="taskType(currentTaskRun)"
                v-if="taskType(currentTaskRun)"
                onlyIcon
                :icons="pluginsStore.icons"
            />
        </div>

        <div
            class="task-id flex-grow-1"
            :id="`attempt-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${currentTaskRun.id}`"
        >
            <el-tooltip :persistent="false" transition="" :hideAfter="0" effect="light">
                <template #content>
                    {{ $t("from") }} :
                    {{ $filters.date(selectedAttempt(currentTaskRun).state.startDate) }}
                    <br>
                    {{ $t("to") }} :
                    {{ $filters.date(selectedAttempt(currentTaskRun).state.endDate) }}
                    <br>
                    <Clock />
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
                <Duration :histories="currentTaskRun.state.histories" />
            </small>
        </div>

        <div class="task-status">
            <Status size="small" :status="currentTaskRun.state.current" />
        </div>

        <slot name="buttons" />

        <el-dropdown trigger="click">
            <el-button type="default" class="task-run-buttons">
                <DotsVertical title="" />
            </el-button>
            <template #dropdown>
                <el-dropdown-menu>
                    <el-dropdown-item
                        v-if="selectedAttempt(currentTaskRun).state.current === 'FAILED'"
                        @click="fixErrorWithAi(currentTaskRun)"
                    >
                        <span class="d-inline-flex align-items-center">
                            <AiIcon class="me-1" />
                            <span>{{ $t('fix_with_ai') }}</span>
                        </span>
                    </el-dropdown-item>
                    <SubFlowLink
                        v-if="isSubflow(currentTaskRun)"
                        component="el-dropdown-item"
                        tabExecution="logs"
                        :executionId="currentTaskRun.outputs.executionId"
                    />

                    <Metrics :taskRun="currentTaskRun" :execution="followedExecution" />

                    <Outputs
                        :outputs="currentTaskRun.outputs"
                        :execution="followedExecution"
                    />

                    <Restart
                        component="el-dropdown-item"
                        :key="`restart-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        isReplay
                        tooltipPosition="left"
                        :execution="followedExecution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="$emit('follow', $event)"
                    />

                    <ChangeStatus
                        component="el-dropdown-item"
                        :key="`change-status-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        :execution="followedExecution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="$emit('follow', $event)"
                    />
                    <TaskEdit
                        v-if="canReadFlow"
                        :readOnly="true"
                        component="el-dropdown-item"
                        :taskId="currentTaskRun.taskId"
                        section="tasks"
                        :flowId="followedExecution.flowId"
                        :namespace="followedExecution.namespace"
                        :revision="followedExecution.flowRevision"
                        :flowSource="flow?.source"
                    />
                    <el-dropdown-item
                        :icon="Download"
                        @click="downloadContent(currentTaskRun.id)"
                    >
                        {{ $t("download logs") }}
                    </el-dropdown-item>
                    <el-dropdown-item
                        :icon="Copy"
                        @click="copyContent(currentTaskRun.id)"
                    >
                        {{ $t("copy logs") }}
                    </el-dropdown-item>
                    <el-dropdown-item
                        :icon="Delete"
                        @click="deleteLogs(currentTaskRun.id)"
                    >
                        {{ $t("delete logs") }}
                    </el-dropdown-item>
                    <WorkerInfo
                        component="el-dropdown-item"
                        v-if="hasWorkerId(currentTaskRun) !== null"
                        :taskRun="currentTaskRun"
                        @follow="$emit('follow', $event)"
                    />
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </div>
    <div class="attempt-header">
        <el-select
            class="d-none d-md-inline-block attempt-select"
            :modelValue="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
            @change="$emit('swapDisplayedAttempt', {taskRunId: currentTaskRun.id, attemptNumber: $event})"
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
            <Status size="small" :status="selectedAttempt(currentTaskRun).state.current" />
        </div>

        <div class="task-duration d-none d-md-inline-block">
            <small class="me-1">
                <Duration :histories="selectedAttempt(currentTaskRun).state.histories" />
            </small>
        </div>
    </div>
</template>

<script>
    import Restart from "./overview/components/actions/Restart.vue";
    import Metrics from "./Metrics.vue";
    import {State, Status} from "@kestra-io/ui-libs";
    import ChangeStatus from "./ChangeStatus.vue";
    import TaskEdit from "../flows/TaskEdit.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue";
    import Outputs from "./Outputs.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import Copy from "vue-material-design-icons/ContentCopy.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import WorkerInfo from "./WorkerInfo.vue";
    import AiIcon from "../ai/AiIcon.vue";
    import FlowUtils from "../../utils/flowUtils";
    import _groupBy from "lodash/groupBy";
    import {TaskIcon, SECTIONS} from "@kestra-io/ui-libs";
    import Duration from "../layout/Duration.vue";
    import Utils from "../../utils/utils";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {usePluginsStore} from "../../stores/plugins";
    import {useCoreStore} from "../../stores/core";
    import {useExecutionsStore} from "../../stores/executions";
    import {mapStores} from "pinia";
    import {useAuthStore} from "override/stores/auth"

    export default {
        components: {
            TaskIcon,
            Outputs,
            SubFlowLink,
            TaskEdit,
            ChangeStatus,
            Status,
            Metrics,
            Restart,
            Duration,
            Clock,
            ChevronRight,
            ChevronDown,
            DotsVertical,
            WorkerInfo,
            AiIcon
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
            ...mapStores(usePluginsStore, useCoreStore, useExecutionsStore, useAuthStore),
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
                return this.authStore.user?.isAllowed(permission.FLOW, action.READ, this.$route.params.namespace)
            },
            Copy() {
                return Copy;
            },
            Delete() {
                return Delete;
            },
            Download() {
                return Download;
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
                this.executionsStore.downloadLogs({
                    executionId: this.followedExecution.id,
                    params: {...params, taskRunId: currentTaskRunId}
                }).then((response) => {
                    Utils.downloadUrl(window.URL.createObjectURL(new Blob([response])), this.downloadName(currentTaskRunId));
                });
            },
            copyContent(currentTaskRunId) {
                const params = this.params
                this.executionsStore.downloadLogs({
                    executionId: this.followedExecution.id,
                    params: {...params, taskRunId: currentTaskRunId}
                }).then((response) => {
                    Utils.copy(response).then(() =>{
                        this.coreStore.message = {
                            variant: "success",
                            title: this.$t("success"),
                            message: this.$t("copied_logs_to_clipboard"),
                        };
                    });
                })
            },
            deleteLogs(currentTaskRunId) {
                const params = this.params
                this.$toast().confirm(
                    this.$t("delete_log"),
                    () => {
                        this.executionsStore.deleteLogs({
                            executionId: this.followedExecution.id,
                            params: {...params, taskRunId: currentTaskRunId}
                        }).then((_) => {
                            this.$emit("update-logs", this.followedExecution.id)
                        });
                    },
                    () => {}
                )

            },
            hasWorkerId(currentTaskRun) {
                return currentTaskRun.attempts?.find(attempt => attempt.workerId !== null) !== null;
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
            },
            fixErrorWithAi(taskRun) {
                const attemptNumber = this.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0;
                const attemptUid = this.attemptUid(taskRun.id, attemptNumber);
                const logs = this.logsWithIndexByAttemptUid[attemptUid] ?? [];
                const errorLine = (() => {
                    const lastError = [...logs].reverse().find(l => (l.level || "").toString().toUpperCase() === "ERROR");
                    if (lastError?.message) return lastError.message;
                    const last = [...logs].reverse().find(l => (l.message ?? "").length > 0);
                    return last?.message ?? "";
                })();
                const prompt = `Fix the task ${taskRun.taskId} as it generated the following error:\n${errorLine}`;
                try {
                    window.sessionStorage.setItem("kestra-ai-prompt", prompt);
                } catch (err) {
                    console.warn("AI prompt not persisted to sessionStorage:", err);
                }

                this.$router.push({
                    name: "flows/update",
                    params: {
                        namespace: this.followedExecution.namespace,
                        id: this.followedExecution.flowId,
                        tab: "edit",
                        tenant: this.$route.params?.tenant,
                    },
                    query: {ai: "open"}
                });
            }
        },
        emits: ["toggleShowAttempt", "swapDisplayedAttempt", "follow", "update-logs"]
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