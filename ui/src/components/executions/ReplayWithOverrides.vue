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
        <div class="replay-with-overrides" data-dialog-wide data-test="replay-with-overrides-dialog">
            <KsText tag="p" size="small" type="info">
                {{ $t("replayOverrides.note", {revision: execution.flowRevision}) }}
            </KsText>

            <KsSelect
                v-model="picked"
                filterable
                :loading="loading"
                :placeholder="$t('replayOverrides.add')"
                :aria-label="$t('replayOverrides.add')"
                data-test="replay-override-add"
                @change="add"
            >
                <KsOption
                    v-for="candidate in candidates"
                    :key="candidate.id"
                    :value="candidate.id"
                    :label="label(candidate)"
                />
            </KsSelect>

            <section
                v-for="entry in entries"
                :key="entry.taskRunId"
                class="entry"
                :data-test="`replay-override-${entry.taskRunId}`"
            >
                <header>
                    <h4 class="entry-title">
                        {{ entry.label }}
                    </h4>
                    <KsIconButton :aria-label="$t('replayOverrides.remove', {task: entry.label})" @click="remove(entry.taskRunId)">
                        <Close />
                    </KsIconButton>
                </header>
                <KsEditor
                    v-bind="editorBindings"
                    v-model="entry.text"
                    :path="`replay-override-${entry.taskRunId}`"
                    :options="{fullHeight: false, showScroll: true}"
                    :inline="true"
                    :navbar="false"
                    lang="json"
                />
                <KsAlert
                    v-if="parse(entry.text) === undefined"
                    type="error"
                    :closable="false"
                    showIcon
                    :title="$t('replayOverrides.invalid')"
                    data-test="replay-override-error"
                />
            </section>
        </div>

        <template #footer>
            <KsButton @click="isOpen = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton
                type="primary"
                :disabled="!changed.length || invalid"
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
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRouter} from "vue-router"
    import {State, dayjs, isDeepEqual, isPlainObject} from "@kestra-io/design-system"
    import Close from "vue-material-design-icons/Close.vue"
    import PlayBoxEditOutline from "vue-material-design-icons/PlayBoxEditOutline.vue"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
    import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
    import type {Execution} from "../../stores/executions"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {useAuthStore} from "override/stores/auth"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {useToast} from "../../utils/toast"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {EXECUTION_PARENT_ROUTE} from "./executionTabs"

    interface Entry {
        taskRunId: string
        label: string
        original: Record<string, unknown>
        text: string
    }

    const props = defineProps<{
        execution: Execution
        taskRun: ExecutionTaskRun
        attemptIndex: number
    }>()

    const {t} = useI18n()
    const router = useRouter()
    const toast = useToast()
    const authStore = useAuthStore()
    const editorBindings = useEditorBindings()

    const isOpen = ref(false)
    const picked = ref<string>()
    const loading = ref(false)
    const sending = ref(false)
    const entries = ref<Entry[]>([])

    const hasPermission = computed(() =>
        !!authStore.user?.isAllowed(resource.EXECUTION, action.REPLAY, props.execution.namespace),
    )

    const enabled = computed(() => {
        if (!props.execution.state || !State.isTerminated(props.execution.state.current)) return false
        const attempts = props.taskRun.attempts
        return !attempts || attempts.length - 1 === props.attemptIndex
    })

    // A task run that ended before this one started is reused by the replay, and is all this task could read.
    const candidates = computed(() => {
        const start = props.taskRun.state?.startDate
        return (props.execution.taskRunList ?? []).filter(taskRun =>
            taskRun.id !== props.taskRun.id
            && !entries.value.some(entry => entry.taskRunId === taskRun.id)
            && !!taskRun.state?.endDate
            && !!start
            && dayjs(taskRun.state.endDate).isSameOrBefore(start),
        )
    })

    const invalid = computed(() => entries.value.some(entry => parse(entry.text) === undefined))

    const changed = computed(() => entries.value.flatMap(entry => {
        const outputs = parse(entry.text)
        return outputs === undefined || isDeepEqual(outputs, entry.original) ? [] : [[entry.taskRunId, outputs] as const]
    }))

    function label(taskRun: ExecutionTaskRun) {
        return taskRun.value ? `${taskRun.taskId} (${taskRun.value})` : taskRun.taskId
    }

    function parse(text: string): Record<string, unknown> | undefined {
        try {
            const value: unknown = JSON.parse(text)
            return isPlainObject(value) ? value as Record<string, unknown> : undefined
        } catch {
            return undefined
        }
    }

    async function add(taskRunId: string) {
        picked.value = undefined
        const taskRun = props.execution.taskRunList?.find(candidate => candidate.id === taskRunId)
        if (!taskRun) return
        loading.value = true
        try {
            const original = await OutputsAPI.taskRunOutputs({executionId: props.execution.id, taskRunId}) as Record<string, unknown>
            entries.value.push({taskRunId, label: label(taskRun), original, text: JSON.stringify(original, null, 4)})
        } finally {
            loading.value = false
        }
    }

    function remove(taskRunId: string) {
        entries.value = entries.value.filter(entry => entry.taskRunId !== taskRunId)
    }

    async function replay() {
        sending.value = true
        try {
            const replayed = await ExecutionsAPI.replayExecutionWithOverrides({
                executionId: props.execution.id,
                taskRunId: props.taskRun.id,
                outputs: Object.fromEntries(changed.value) as Parameters<typeof ExecutionsAPI.replayExecutionWithOverrides>[0]["outputs"],
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

.entry {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
}

.entry > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.entry-title {
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-semibold);
    margin: 0;
    color: var(--ks-text-primary);
}
</style>
