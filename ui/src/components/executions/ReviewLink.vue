<template>
    <ReviewDialog
        v-if="opened"
        v-model="dialogOpen"
        :execution="execution"
        :taskRun="opened.taskRun"
        :task="opened.task"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import ReviewDialog from "./ReviewDialog.vue"
    import {useApprovalReviewer} from "../../composables/useApprovalReviewer"
    import {findApprovalTask, type ApprovalTask, type ApprovalTaskRun} from "../../utils/approval"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {useToast} from "../../utils/toast"

    const props = defineProps<{
        execution: Execution;
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()
    const toast = useToast()
    const {enabled} = useApprovalReviewer(() => props.execution)

    const dialogOpen = ref(false)
    const opened = ref<{taskRun: ApprovalTaskRun; task: ApprovalTask}>()

    const requested = computed(() => {
        const requestedId = route.query.review
        const taskRun = typeof requestedId === "string" ? props.execution.taskRunList?.find(candidate => candidate.id === requestedId) : undefined
        const task = taskRun ? findApprovalTask(executionsStore.flow, taskRun.taskId) : undefined
        return taskRun && task ? {taskRun: taskRun as unknown as ApprovalTaskRun, task, paused: taskRun.state.current === "PAUSED"} : undefined
    })

    watch(
        [enabled, requested],
        ([isEnabled, found]) => {
            if (found === undefined || (found.paused && !isEnabled)) {
                return
            }
            if (found.paused) {
                opened.value = {taskRun: found.taskRun, task: found.task}
                dialogOpen.value = true
            } else {
                toast.warning(t("approval.already decided"))
            }
            const {review: _review, ...query} = route.query
            router.replace({query})
        },
        {immediate: true},
    )

    watch(dialogOpen, (open) => {
        if (!open) {
            opened.value = undefined
        }
    })
</script>
