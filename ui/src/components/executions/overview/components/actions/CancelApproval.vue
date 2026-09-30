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
    import {useI18n} from "vue-i18n"
    import CancelIcon from "vue-material-design-icons/Cancel.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import {useApprovalReviewer} from "../../../../../composables/useApprovalReviewer"
    import {useExecutionsStore, type Execution} from "../../../../../stores/executions"
    import {useToast} from "../../../../../utils/toast"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        execution: Execution;
    }>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const toast = useToast()

    const {approval, enabled} = useApprovalReviewer(() => props.execution)

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
