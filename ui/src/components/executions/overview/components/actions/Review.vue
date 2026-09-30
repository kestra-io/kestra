<template>
    <NavBarAction
        v-if="enabled"
        v-bind="$attrs"
        :icon="ClipboardCheckOutline"
        data-test="review-action"
        @click="dialogOpen = true"
    >
        {{ $t("approval.review") }}
    </NavBarAction>

    <ReviewDialog
        v-if="approval"
        v-model="dialogOpen"
        :execution="execution"
        :taskRun="approval.taskRun"
        :task="approval.task"
    />
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import ClipboardCheckOutline from "vue-material-design-icons/ClipboardCheckOutline.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import ReviewDialog from "../../../ReviewDialog.vue"
    import {useApprovalReviewer} from "../../../../../composables/useApprovalReviewer"
    import type {Execution} from "../../../../../stores/executions"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        execution: Execution;
    }>()

    const dialogOpen = ref(false)

    const {approval, enabled} = useApprovalReviewer(() => props.execution)
</script>
