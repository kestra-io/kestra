<template>
    <NavBarAction
        v-if="enabled"
        v-bind="$attrs"
        :icon="ClipboardCheckOutline"
        data-test="review-action"
        @click="open"
    >
        {{ $t("approval.review") }}
    </NavBarAction>

    <ReviewDialog
        v-if="opened"
        v-model="dialogOpen"
        :execution="execution"
        :taskRun="opened.taskRun"
        :task="opened.task"
        @closed="opened = undefined"
    />
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import ClipboardCheckOutline from "vue-material-design-icons/ClipboardCheckOutline.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import ReviewDialog from "../../../ReviewDialog.vue"
    import {useApprovalReviewer} from "../../../../../composables/useApprovalReviewer"
    import type {ApprovalTask, ApprovalTaskRun} from "../../../../../utils/approval"
    import type {Execution} from "../../../../../stores/executions"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        execution: Execution;
    }>()

    const dialogOpen = ref(false)
    const opened = ref<{taskRun: ApprovalTaskRun; task: ApprovalTask}>()

    const {approval, enabled} = useApprovalReviewer(() => props.execution)

    function open() {
        opened.value = approval.value
        dialogOpen.value = true
    }
</script>
