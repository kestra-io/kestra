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
        :dirty="changed.length > 0"
        :title="$t('replayOverrides.dialog_title', {taskId: taskRun.taskId})"
    >
        <div v-ks-loading="loading" class="replay-with-overrides" data-test="replay-with-overrides-dialog">
            <KsText tag="p" size="small" type="info">
                {{ $t("replayOverrides.note", {revision: execution.flowRevision}) }}
            </KsText>

            <KsAlert
                v-if="failed"
                type="error"
                :closable="false"
                showIcon
                :title="$t('replayOverrides.load_error')"
            />

            <KsNoData
                v-else-if="!loading && !rows.length"
                :title="$t('replayOverrides.empty_title')"
                :description="$t('replayOverrides.empty', {taskId: taskRun.taskId})"
                data-test="replay-overrides-empty"
            />

            <div
                v-for="row in rows"
                :key="row.path"
                class="row"
                :data-test="`replay-override-${row.path}`"
            >
                <code>{{ row.path }}</code>
                <KsInput
                    v-model="texts[row.path]"
                    :aria-label="row.path"
                    :type="isObject(row.original) ? 'textarea' : 'text'"
                    :autosize="{minRows: 1, maxRows: 6}"
                />
                <KsText
                    v-if="errors[row.path]"
                    size="small"
                    type="danger"
                    data-test="replay-override-error"
                >
                    {{ $t(`replayOverrides.invalid_${errors[row.path]}`) }}
                </KsText>
            </div>
        </div>

        <template #footer>
            <KsButton @click="isOpen = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton
                type="primary"
                :disabled="!changed.length || Object.keys(errors).length > 0"
                :loading="sending"
                data-test="replay-confirm"
                @click="replay"
            >
                {{ $t("replayOverrides.action") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRouter} from "vue-router"
    import {State, dayjs, groupBy, isDeepEqual} from "@kestra-io/design-system"
    import PlayBoxEditOutline from "vue-material-design-icons/PlayBoxEditOutline.vue"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
    import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
    import type {Execution} from "../../stores/executions"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {useFlowStore} from "../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {EXECUTION_PARENT_ROUTE} from "./executionTabs"
    import {editableText, findTask, ownTemplates, parseOverride, patchedOutputs, readPaths, valueAt, type OutputPath} from "./replayOverrides"

    interface Row extends OutputPath {
        taskRunId: string
        original: unknown
    }

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
    const loading = ref(false)
    const failed = ref(false)
    const sending = ref(false)
    const rows = ref<Row[]>([])
    const outputsByTaskRun = ref<Record<string, Record<string, unknown>>>({})
    const texts = ref<Record<string, string>>({})

    const hasPermission = computed(() =>
        !!authStore.user?.isAllowed(resource.EXECUTION, action.REPLAY, props.execution.namespace),
    )

    const enabled = computed(() => {
        if (!props.execution.state || !State.isTerminated(props.execution.state.current)) return false
        const attempts = props.taskRun.attempts
        return !attempts || attempts.length - 1 === props.attemptIndex
    })

    const parsed = computed(() => Object.fromEntries(rows.value.map(row => [row.path, parseOverride(row.original, texts.value[row.path] ?? "")])))

    const errors = computed(() => Object.fromEntries(Object.entries(parsed.value).flatMap(([path, result]) => result.error ? [[path, result.error]] : [])))

    const changed = computed(() => rows.value.filter(row => {
        const result = parsed.value[row.path]
        return !result.error && !isDeepEqual(result.value, row.original ?? null)
    }))

    function isObject(value: unknown) {
        return value !== null && typeof value === "object"
    }

    // The replay reuses a task run that ended before this one started; a task with several such runs is read through a dynamic key.
    function reusedTaskRuns() {
        const start = props.taskRun.state?.startDate
        const reused = (props.execution.taskRunList ?? []).filter(taskRun =>
            taskRun.id !== props.taskRun.id
            && !!start
            && !!taskRun.state?.endDate
            && dayjs(taskRun.state.endDate).isSameOrBefore(start),
        )
        return new Map(Object.entries(groupBy(reused, taskRun => taskRun.taskId)).flatMap(([taskId, runs]) => runs.length === 1 ? [[taskId, runs[0]]] : []))
    }

    watch(isOpen, async open => {
        if (!open || rows.value.length || failed.value) return
        loading.value = true
        try {
            const flow = await flowStore.loadFlow({
                namespace: props.execution.namespace,
                id: props.execution.flowId,
                revision: String(props.execution.flowRevision),
                store: false,
            })
            const task = findTask(flow, props.taskRun.taskId)
            const reused = reusedTaskRuns()
            const paths = task ? readPaths(ownTemplates(task)).filter(path => reused.has(path.taskId)) : []

            const outputs = Object.fromEntries(await Promise.all([...new Set(paths.map(path => reused.get(path.taskId)!.id))].map(async taskRunId =>
                [taskRunId, await OutputsAPI.taskRunOutputs({executionId: props.execution.id, taskRunId}) as Record<string, unknown>] as const,
            )))
            outputsByTaskRun.value = outputs
            rows.value = paths.map(path => {
                const taskRunId = reused.get(path.taskId)!.id
                return {...path, taskRunId, original: valueAt(outputs[taskRunId] ?? {}, path.keys)}
            })
            texts.value = Object.fromEntries(rows.value.map(row => [row.path, editableText(row.original)]))
        } catch {
            failed.value = true
        } finally {
            loading.value = false
        }
    })

    async function replay() {
        sending.value = true
        try {
            const byTaskRun = groupBy(changed.value, row => row.taskRunId)
            const outputs = Object.fromEntries(Object.entries(byTaskRun).map(([taskRunId, changes]) => [
                taskRunId,
                patchedOutputs(outputsByTaskRun.value[taskRunId] ?? {}, changes.map(row => ({keys: row.keys, value: parsed.value[row.path].value}))),
            ]))
            const replayed = await ExecutionsAPI.replayExecutionWithOverrides({
                executionId: props.execution.id,
                taskRunId: props.taskRun.id,
                outputs: outputs as Parameters<typeof ExecutionsAPI.replayExecutionWithOverrides>[0]["outputs"],
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
    min-height: 4rem;
}

.replay-with-overrides > p {
    margin: 0;
}

.row {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
}

.row > code {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
}
</style>
