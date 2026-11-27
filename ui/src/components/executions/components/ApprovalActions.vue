<template>
    <KsButton
        v-if="link"
        size="small"
        tag="router-link"
        :to="link.to"
        :icon="OpenInNew"
        data-test="open-approval"
    >
        {{ link.label }}
    </KsButton>
    <KsButton
        v-if="enabled"
        size="small"
        type="primary"
        :icon="ClipboardCheckOutline"
        data-test="approval-review"
        @click="openReview"
    >
        {{ $t("approval.review") }}
    </KsButton>

    <ReviewDialog
        v-if="opened"
        v-model="dialogOpen"
        :execution
        :taskRun="opened.taskRun"
        :task="opened.task"
        @closed="opened = undefined"
    />
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import ClipboardCheckOutline from "vue-material-design-icons/ClipboardCheckOutline.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import ReviewDialog from "../ReviewDialog.vue"
    import {useApprovalReviewer} from "../../../composables/useApprovalReviewer"
    import type {ApprovalTask, ApprovalTaskRun} from "../../../utils/approval"
    import type {Execution} from "../../../stores/executions"
    import {useApprovalLink} from "override/components/executions/overview/OverviewExtensions"

    const props = defineProps<{
        execution: Execution;
        taskRun: {id: string};
    }>()

    const {approval, enabled} = useApprovalReviewer(() => props.execution, () => props.taskRun.id)
    const link = useApprovalLink(() => props.execution, () => props.taskRun)

    const dialogOpen = ref(false)
    const opened = ref<{taskRun: ApprovalTaskRun; task: ApprovalTask}>()

    function openReview() {
        opened.value = approval.value
        dialogOpen.value = true
    }
</script>
