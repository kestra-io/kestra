import {computed, ref, watch} from "vue"
import resource from "../models/resource"
import action from "../models/action"
import {findApprovalTaskRun} from "../utils/approval"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {useAuthStore} from "override/stores/auth"

export function useApprovalReviewer(execution: () => Execution) {
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const approval = computed(() => findApprovalTaskRun(execution(), executionsStore.flow, "PAUSED"))
    const allowed = ref(false)

    watch(
        () => [execution().id, approval.value?.taskRun.id] as const,
        async ([id, taskRunId]) => {
            allowed.value = false
            if (id === undefined || taskRunId === undefined) {
                return
            }
            const isAllowed = await executionsStore.reviewAllowed({id, taskRunId}).catch(() => false)
            if (id === execution().id && taskRunId === approval.value?.taskRun.id) {
                allowed.value = isAllowed
            }
        },
        {immediate: true},
    )

    const enabled = computed(() =>
        approval.value !== undefined && allowed.value && authStore.user?.isAllowed(resource.EXECUTION, action.REVIEW, execution().namespace) === true,
    )

    return {approval, enabled}
}
