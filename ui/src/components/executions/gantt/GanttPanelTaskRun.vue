<template>
    <div class="panel-task-run">
        <GanttPanelFailure v-if="isFailed && context.execution.value" :node="node" :execution="context.execution.value" />

        <KsTabs v-model="activeTab" type="box" data-test="gantt-task-run-tabs">
            <KsTabPane name="task-logs" lazy :label="$t('logs')">
                <div class="panel-logs">
                    <GanttTaskLogs
                        v-if="!isIterationTask && execution"
                        :key="node.key"
                        :execution="execution"
                        :taskRun="node.taskRun"
                        :taskType="node.taskType"
                        :flow="executionsStore.flow"
                        :levelFilter="tree.levelFilter.value"
                    />
                    <TaskRunDetails
                        v-else-if="isIterationTask"
                        :key="node.key"
                        :targetExecutionId="node.executionId"
                        :taskRunId="node.taskRun.id"
                        :targetFlow="executionsStore.flow"
                        :levelFilter="tree.levelFilter.value"
                        hideTaskHeader
                        :excludeMetas="LOG_EXCLUDED_METAS"
                    />
                </div>
            </KsTabPane>

            <KsTabPane name="task-attempts" lazy :label="$t('gantt_panel.tabs.attempts')">
                <div class="tab-content">
                    <section>
                        <h3>{{ $t("gantt_panel.tabs.attempts") }}</h3>
                        <FailureAttempts :taskRun="node.taskRun" />
                    </section>
                    <section>
                        <h3>{{ $t("state_history.title") }}</h3>
                        <FailureStateHistory :taskRun="node.taskRun" />
                    </section>
                </div>
            </KsTabPane>

            <KsTabPane name="task-configuration" lazy :label="$t('gantt_panel.tabs.configuration')">
                <div v-if="context.execution.value" class="tab-content">
                    <FailureResolvedConfig
                        :rawBlock="context.rawTaskBlock.value"
                        :flowLoading="context.flowLoading.value"
                        :flowError="context.flowError.value"
                        :executionId="context.execution.value.id"
                        :taskRunId="node.taskRun.id"
                    />
                </div>
            </KsTabPane>

            <KsTabPane name="task-inputs-outputs" lazy :label="$t('gantt_panel.tabs.inputs_outputs')">
                <div v-if="context.execution.value" class="tab-content">
                    <section>
                        <h3>{{ $t("gantt_panel.execution_inputs") }}</h3>
                        <FailureExecutionInputs :inputIds="context.inputIds.value" :executionId="context.execution.value.id" />
                    </section>
                    <section>
                        <h3>{{ $t("gantt_panel.consumed_outputs") }}</h3>
                        <FailureUpstreamOutputs
                            :referencedTaskIds="context.referencedOutputTaskIds.value"
                            :taskRunList="context.taskRuns.value"
                            :executionId="context.execution.value.id"
                        />
                    </section>
                </div>
            </KsTabPane>

            <KsTabPane name="task-structure" lazy :label="$t('gantt_panel.tabs.structure')">
                <div class="tab-content">
                    <FailureStructuralImpact
                        :nodes="context.structuralNodes.value"
                        :focusedId="node.taskRun.id"
                        :flow="executionsStore.flow"
                        :rawBlocks="context.structuralRawBlocks.value"
                        @focus="context.selectTaskRun"
                    />
                </div>
            </KsTabPane>
        </KsTabs>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsTabPane, KsTabs, State} from "@kestra-io/design-system"
    import type {Log} from "../../../stores/logs"
    import {useExecutionsStore} from "../../../stores/executions"
    import GanttTaskLogs from "../GanttTaskLogs.vue"
    import TaskRunDetails from "../../logs/TaskRunDetails.vue"
    import GanttPanelFailure from "./GanttPanelFailure.vue"
    import FailureAttempts from "./FailureAttempts.vue"
    import FailureStateHistory from "./FailureStateHistory.vue"
    import FailureResolvedConfig from "./FailureResolvedConfig.vue"
    import FailureExecutionInputs from "./FailureExecutionInputs.vue"
    import FailureUpstreamOutputs from "./FailureUpstreamOutputs.vue"
    import FailureStructuralImpact from "./FailureStructuralImpact.vue"
    import {useTaskRunContext} from "./useTaskRunContext"
    import {owningIteration, useGanttTree, type TaskRunNode} from "./ganttTree"

    const LOG_EXCLUDED_METAS: Array<keyof Log> = ["namespace", "flowId", "taskId", "executionId"]
    const FAILED_STATES: string[] = [State.FAILED, State.KILLED]
    const FLOW_DEPENDENT_TABS = ["task-configuration", "task-inputs-outputs", "task-structure"]

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const executionsStore = useExecutionsStore()
    const execution = computed(() => executionsStore.execution)
    const isIterationTask = computed(() => owningIteration(props.node) !== undefined)
    const isFailed = computed(() => FAILED_STATES.includes(props.node.taskRun.state.current))

    const activeTab = ref("task-logs")
    const flowEnabled = ref(false)
    const context = useTaskRunContext(() => props.node, flowEnabled)

    watch(activeTab, (tab) => {
        if (FLOW_DEPENDENT_TABS.includes(tab)) flowEnabled.value = true
    })
</script>

<style scoped>
    .panel-logs {
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
    }

    .tab-content {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);

        & h3 {
            margin: 0 0 var(--ks-spacing-2);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-secondary);
        }
    }
</style>
