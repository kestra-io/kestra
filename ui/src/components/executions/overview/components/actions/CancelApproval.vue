<template>
    <NavBarAction
        v-if="enabled"
        v-bind="$attrs"
        :icon="CancelIcon"
        data-test="cancel-approval-action"
        @click="click"
    >
        {{ $t("approval.cancel request") }}
    </NavBarAction>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import CancelIcon from "vue-material-design-icons/Cancel.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    import {findApprovalTaskRun} from "../../../../../utils/approval"
    import {useExecutionsStore, type Execution} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../../../../utils/toast"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        execution: Execution;
    }>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const toast = useToast()

    const approval = computed(() => findApprovalTaskRun(props.execution, executionsStore.flow, "PAUSED"))

    const enabled = computed(() =>
        approval.value !== undefined && authStore.user?.isAllowed(resource.EXECUTION, action.REVIEW, props.execution.namespace) === true,
    )

    function click() {
        const taskRunId = approval.value?.taskRun.id
        if (taskRunId === undefined) {
            return
        }

        toast.confirm(t("approval.cancel confirm", {id: props.execution.id}), async () => {
            await executionsStore.cancelApproval({id: props.execution.id, taskRunId})
            toast.success(t("approval.cancel done"))
        })
    }
</script>
