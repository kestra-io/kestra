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
    import {computed, onMounted, ref} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import ClipboardCheckOutline from "vue-material-design-icons/ClipboardCheckOutline.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import ReviewDialog from "../../../ReviewDialog.vue"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    import {findApprovalTaskRun} from "../../../../../utils/approval"
    import {useExecutionsStore, type Execution} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        execution: Execution;
    }>()

    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const dialogOpen = ref(false)

    const approval = computed(() => findApprovalTaskRun(props.execution, executionsStore.flow, "PAUSED"))

    const enabled = computed(() =>
        approval.value !== undefined && authStore.user?.isAllowed(resource.EXECUTION, action.REVIEW, props.execution.namespace) === true,
    )

    onMounted(() => {
        const requested = route.query.review
        if (typeof requested === "string" && requested === approval.value?.taskRun.id) {
            dialogOpen.value = true
            const {review: _review, ...query} = route.query
            router.replace({query})
        }
    })
</script>
