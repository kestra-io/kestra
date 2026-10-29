import {computed, ref, watch} from "vue"
import resource from "../models/resource"
import action from "../models/action"
import {findApprovalTask, findApprovalTaskRun, type ApprovalTaskRun} from "../utils/approval"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {useAuthStore} from "override/stores/auth"

export function useApprovalReviewer(execution: () => Execution, taskRunId?: () => string | undefined) {
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const approval = computed(() => {
        const requestedId = taskRunId?.()
        if (requestedId === undefined) {
            return findApprovalTaskRun(execution(), executionsStore.flow, "PAUSED")
        }
        const taskRun = execution().taskRunList?.find(candidate => candidate.id === requestedId)
        const task = taskRun?.state.current === "PAUSED" ? findApprovalTask(executionsStore.flow, taskRun.taskId) : undefined
        return taskRun && task ? {taskRun: taskRun as unknown as ApprovalTaskRun, task} : undefined
    })
    const permitted = computed(() => authStore.user?.isAllowed(resource.EXECUTION, action.REVIEW, execution().namespace) === true)
    const allowed = ref(false)
    const checkedTaskRunId = ref<string>()

    watch(
        [permitted, () => execution().id, () => approval.value?.taskRun.id],
        async ([isPermitted, id, approvalTaskRunId]) => {
            allowed.value = false
            checkedTaskRunId.value = undefined
            if (!isPermitted || id === undefined || approvalTaskRunId === undefined) {
                return
            }
            const isAllowed = await executionsStore.isReviewAllowed({id, taskRunId: approvalTaskRunId})
            if (id === execution().id && approvalTaskRunId === approval.value?.taskRun.id) {
                allowed.value = isAllowed
                checkedTaskRunId.value = approvalTaskRunId
            }
        },
        {immediate: true},
    )

    const checked = computed(() => !permitted.value || approval.value === undefined || checkedTaskRunId.value === approval.value.taskRun.id)
    const enabled = computed(() => approval.value !== undefined && permitted.value && allowed.value)

    return {approval, enabled, checked}
}
