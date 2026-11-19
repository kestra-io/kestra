<template>
    <KsDropdown
        v-if="enabled && link"
        splitButton
        size="small"
        data-test="approval-review"
        @click="openReview"
    >
        <KsIcon><ClipboardCheckOutline /></KsIcon>
        <span>{{ $t("approval.review") }}</span>
        <template #dropdown>
            <KsDropdownMenu>
                <KsDropdownItem :icon="OpenInNew" data-test="open-approval" @click="router.push(link.to)">
                    {{ link.label }}
                </KsDropdownItem>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
    <KsButton
        v-else-if="enabled"
        size="small"
        :icon="ClipboardCheckOutline"
        data-test="approval-review"
        @click="openReview"
    >
        {{ $t("approval.review") }}
    </KsButton>
    <KsButton
        v-else-if="link"
        size="small"
        tag="router-link"
        :to="link.to"
        :icon="OpenInNew"
        data-test="open-approval"
    >
        {{ link.label }}
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
    import {useRouter} from "vue-router"
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

    const router = useRouter()

    const {approval, enabled} = useApprovalReviewer(() => props.execution, () => props.taskRun.id)
    const link = useApprovalLink(() => props.execution, () => props.taskRun)

    const dialogOpen = ref(false)
    const opened = ref<{taskRun: ApprovalTaskRun; task: ApprovalTask}>()

    function openReview() {
        opened.value = approval.value
        dialogOpen.value = true
    }
</script>
