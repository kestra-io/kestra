<template>
    <ReviewDialog
        v-if="opened?.paused"
        v-model="dialogOpen"
        :execution="execution"
        :taskRun="opened.taskRun"
        :task="opened.task"
        @closed="opened = undefined"
    />
    <ReviewedDialog
        v-else-if="opened"
        v-model="dialogOpen"
        :execution="execution"
        :taskRun="opened.taskRun"
        @closed="opened = undefined"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {State} from "@kestra-io/design-system"
    import ReviewDialog from "./ReviewDialog.vue"
    import ReviewedDialog from "./ReviewedDialog.vue"
    import {useApprovalReviewer} from "../../composables/useApprovalReviewer"
    import {findApprovalTask, type ApprovalTask, type ApprovalTaskRun} from "../../utils/approval"
    import {useExecutionsStore, type Execution} from "../../stores/executions"

    const props = defineProps<{
        execution: Execution;
    }>()

    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()

    const requestedId = computed(() => typeof route.query.review === "string" ? route.query.review : undefined)
    const {enabled, checked} = useApprovalReviewer(() => props.execution, () => requestedId.value)

    const dialogOpen = ref(false)
    const opened = ref<{taskRun: ApprovalTaskRun; task: ApprovalTask; paused: boolean}>()

    const requested = () => {
        const taskRun = requestedId.value === undefined ? undefined : props.execution.taskRunList?.find(candidate => candidate.id === requestedId.value)
        const task = taskRun ? findApprovalTask(executionsStore.flow, taskRun.taskId) : undefined
        return taskRun && task ? {taskRun, task} : undefined
    }

    const requestKey = computed(() => {
        const found = requested()
        return found ? `${found.taskRun.id}:${found.taskRun.state.current}:${checked.value}:${enabled.value}` : undefined
    })

    const clearRequest = () => {
        const {review: _review, ...query} = route.query
        router.replace({query})
    }

    const isUnknownRequest = computed(() => requestedId.value !== undefined
        && props.execution.taskRunList !== undefined
        && executionsStore.flow?.namespace === props.execution.namespace
        && executionsStore.flow?.id === props.execution.flowId
        && requested() === undefined)

    watch(isUnknownRequest, unknown => unknown && clearRequest(), {immediate: true})

    watch(
        requestKey,
        () => {
            const found = requested()
            const state = found?.taskRun.state.current
            if (found === undefined || (state as string) === State.PAUSING || (state === "PAUSED" && !checked.value)) {
                return
            }
            clearRequest()
            if (state === "PAUSED" && !enabled.value) {
                return
            }
            opened.value = {taskRun: found.taskRun as unknown as ApprovalTaskRun, task: found.task, paused: state === "PAUSED"}
            dialogOpen.value = true
        },
        {immediate: true},
    )
</script>
