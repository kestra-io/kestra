<template>
    <KsDropdownItem
        v-if="hasPermission"
        :icon="PlayBoxEditOutline"
        :disabled="!enabled"
        data-test="replay-with-overrides"
        @click="isOpen = true"
    >
        {{ $t("replayOverrides.action") }}
    </KsDropdownItem>

    <KsDialog
        v-if="isOpen"
        v-model="isOpen"
        destroyOnClose
        :appendToBody="true"
        :dirty="overrideCount > 0"
        :title="$t('replayOverrides.dialog_title', {taskId: taskRun.taskId})"
    >
        <div class="replay-with-overrides" data-dialog-wide data-test="replay-with-overrides-dialog">
            <KsText tag="p" size="small" type="info">
                {{ $t("replayOverrides.revision_note", {revision: execution.flowRevision}) }}
            </KsText>

            <section v-ks-loading="loadingSource" class="task-code">
                <h4 class="section-title">
                    {{ $t("replayOverrides.task_code", {taskId: taskRun.taskId}) }}
                </h4>
                <KsAlert
                    v-if="sourceFailed"
                    type="error"
                    :closable="false"
                    showIcon
                    :title="$t('replayOverrides.code_error', {taskId: taskRun.taskId})"
                />
                <div v-else-if="taskSource" class="code" data-test="replay-with-overrides-code">
                    <KsEditor
                        :modelValue="taskSource"
                        lang="yaml"
                        readOnly
                        :navbar="false"
                        :options="{fullHeight: false}"
                    />
                </div>
            </section>

            <ReplayOverrides
                v-model:overrides="overrides"
                v-model:invalid="invalid"
                :executionId="execution.id"
                :taskRunId="taskRun.id"
                :taskId="taskRun.taskId"
            />
        </div>

        <template #footer>
            <KsButton @click="isOpen = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton
                type="primary"
                :disabled="!overrideCount || invalid"
                :loading="sending"
                data-test="replay-confirm"
                @click="replay"
            >
                {{ overrideCount ? $t("replayOverrides.confirm", {count: overrideCount}, overrideCount) : $t("replayOverrides.action") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRouter} from "vue-router"
    import {State} from "@kestra-io/design-system"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import PlayBoxEditOutline from "vue-material-design-icons/PlayBoxEditOutline.vue"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
    import type {Execution} from "../../stores/executions"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {useFlowStore} from "../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {EXECUTION_PARENT_ROUTE} from "./executionTabs"
    import ReplayOverrides from "./ReplayOverrides.vue"

    const props = defineProps<{
        execution: Execution
        taskRun: ExecutionTaskRun
        attemptIndex: number
    }>()

    const {t} = useI18n()
    const router = useRouter()
    const toast = useToast()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()

    const isOpen = ref(false)
    const overrides = ref<Record<string, unknown>>({})
    const invalid = ref(false)
    const sending = ref(false)
    const loadingSource = ref(false)
    const sourceFailed = ref(false)
    const taskSource = ref<string>()

    const overrideCount = computed(() => Object.keys(overrides.value).length)

    const hasPermission = computed(() =>
        !!authStore.user?.isAllowed(resource.EXECUTION, action.REPLAY, props.execution.namespace),
    )

    const enabled = computed(() => {
        if (!props.execution.state || !State.isTerminated(props.execution.state.current)) return false
        const attempts = props.taskRun.attempts
        return !attempts || attempts.length - 1 === props.attemptIndex
    })

    watch(isOpen, async open => {
        if (!open || taskSource.value !== undefined) return
        loadingSource.value = true
        try {
            const flow = await flowStore.loadFlow({
                namespace: props.execution.namespace,
                id: props.execution.flowId,
                revision: String(props.execution.flowRevision),
                store: false,
            })
            taskSource.value = YAML_UTILS.extractBlock({source: flow?.source ?? "", section: "tasks", key: props.taskRun.taskId})
        } catch {
            sourceFailed.value = true
        } finally {
            loadingSource.value = false
        }
    })

    async function replay() {
        sending.value = true
        try {
            const replayed = await ExecutionsAPI.replayExecutionWithOverrides({
                executionId: props.execution.id,
                taskRunId: props.taskRun.id,
                overrides: overrides.value as Parameters<typeof ExecutionsAPI.replayExecutionWithOverrides>[0]["overrides"],
            })
            isOpen.value = false
            toast.success(t("replayed"))
            window.location.href = router.resolve({
                name: EXECUTION_PARENT_ROUTE,
                params: {
                    namespace: replayed.namespace,
                    flowId: replayed.flowId,
                    id: replayed.id,
                    tenant: router.currentRoute.value.params.tenant,
                },
            }).href
        } finally {
            sending.value = false
        }
    }
</script>

<style scoped>
.replay-with-overrides {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
}

.replay-with-overrides > p {
    margin: 0;
}

.section-title {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    margin: 0 0 var(--ks-spacing-2);
    color: var(--ks-text-primary);
}

.code {
    max-height: 18rem;
    overflow: auto;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
}
</style>
