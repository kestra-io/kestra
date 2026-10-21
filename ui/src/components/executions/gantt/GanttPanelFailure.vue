<template>
    <section class="panel-failure" data-test="gantt-task-run-failure">
        <FailureErrorSummary :text="error.text.value" :loading="error.loading.value" />
        <div class="failure-actions">
            <Restart
                component="KsButton"
                isReplay
                tooltipPosition="bottom"
                :execution="execution"
                :taskRun="node.taskRun"
                :attemptIndex="lastAttemptIndex"
            />
            <KsButton
                v-if="canEditFlow"
                tag="router-link"
                link
                :to="editFlowRoute"
                :icon="Pencil"
                data-test="gantt-failure-edit-flow"
            >
                {{ $t("edit flow") }}
            </KsButton>
            <SubFlowLink
                v-if="subflowExecutionId"
                component="KsButton"
                link
                showLabel
                tabExecution="gantt"
                :executionId="subflowExecutionId"
            />
            <KsButton
                v-if="canUseCopilot"
                link
                :icon="AiIcon"
                :disabled="!error.text.value"
                data-test="gantt-failure-copilot"
                @click="askCopilot"
            >
                {{ $t("fix_with_ai") }}
            </KsButton>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    import {KsButton} from "@kestra-io/design-system"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import AiIcon from "../../ai/AiIcon.vue"
    import SubFlowLink from "../../flows/SubFlowLink.vue"
    import Restart from "../overview/components/actions/Restart.vue"
    import FailureErrorSummary from "./FailureErrorSummary.vue"
    import {useTaskRunError} from "./useTaskRunError"
    import {SUBFLOW_TYPE, type TaskRunNode} from "./ganttTree"
    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import {loadTaskRunOutputs} from "../../../composables/useTaskRunOutputs"
    import type {Execution} from "../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"

    const props = defineProps<{
        node: TaskRunNode;
        execution: Execution;
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()

    const error = useTaskRunError(() => props.execution.id, () => props.node.taskRun.id)

    const lastAttemptIndex = computed(() => Math.max(0, (props.node.taskRun.attempts?.length ?? 1) - 1))
    const canUseCopilot = computed(() => !!authStore.user?.hasAny(resource.COPILOT))
    const canEditFlow = computed(() => authStore.user?.isAllowed(resource.FLOW, action.UPDATE, props.execution.namespace))
    const editFlowRoute = computed(() => ({
        name: "flows/update/edit",
        params: {
            namespace: props.execution.namespace,
            id: props.execution.flowId,
            tenant: route.params.tenant as string,
        },
        query: {editTask: props.node.taskRun.taskId},
    }))

    const subflowExecutionId = ref<string | undefined>(undefined)

    watch(
        [() => props.execution.id, () => props.node.taskRun.id],
        async ([executionId, taskRunId], _previous, onCleanup) => {
            subflowExecutionId.value = undefined
            if (props.node.taskType !== SUBFLOW_TYPE) return

            let cancelled = false
            onCleanup(() => { cancelled = true })
            const outputs = await loadTaskRunOutputs(executionId, taskRunId).catch(() => ({}) as Record<string, unknown>)
            const childExecutionId = outputs.executionId
            if (!cancelled && typeof childExecutionId === "string" && childExecutionId.length > 0) {
                subflowExecutionId.value = childExecutionId
            }
        },
        {immediate: true},
    )

    function askCopilot() {
        if (!error.text.value) return
        const {taskId} = props.node.taskRun
        const prompt = `Fix the task ${taskId} as it generated the following error:\n${error.text.value}`
        miscStore.promptCopilot(prompt, {title: t("ai.copilot.fixThread.task", {id: taskId}), newThread: true})
    }
</script>

<style scoped>
    .panel-failure {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        margin: var(--ks-spacing-3) var(--ks-spacing-4) 0;
        padding: var(--ks-spacing-3);
        border: var(--ks-border-width-thin) solid var(--ks-border-error);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-error);
    }

    .failure-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2) var(--ks-spacing-3);
    }
</style>
