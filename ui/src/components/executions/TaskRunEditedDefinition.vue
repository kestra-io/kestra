<template>
    <KsTooltip :content="tooltip">
        <KsTag
            size="small"
            type="warning"
            class="edited-tag"
            role="button"
            tabindex="0"
            data-test="task-run-edited-definition"
            @click.stop="open"
            @keydown.enter.stop="open"
        >
            {{ $t("replayEditedTask.recorded") }}
        </KsTag>
    </KsTooltip>

    <KsDialog
        v-if="isOpen"
        v-model="isOpen"
        destroyOnClose
        :appendToBody="true"
        large
        :title="$t('replayEditedTask.recorded_title', {taskId})"
    >
        <div v-ks-loading="loading" class="edited-definition" data-test="task-run-edited-definition-detail">
            <KsText v-if="hidden" tag="p" size="small" type="info" data-test="task-run-edited-definition-hidden">
                {{ $t("replayEditedTask.recorded_hidden") }}
            </KsText>
            <DiffView v-else-if="edited !== undefined" :oldValue="original ?? ''" :newValue="edited" />
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {asProblem, type TaskRunDefinitionEdit} from "@kestra-io/kestra-sdk"
    import {useExecutionsStore} from "../../stores/executions"
    import {useFlowStore} from "../../stores/flow"
    import {handled} from "../../utils/kestraHttp"
    import {date as dateFilter} from "../../utils/filters"
    import DiffView from "../ai/copilot/DiffView.vue"
    import {extractTaskDefinition} from "./replayEditedTask"

    const props = defineProps<{
        executionId: string
        namespace: string
        flowId: string
        revision: number
        taskRunId: string
        taskId: string
        edit: TaskRunDefinitionEdit
    }>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const flowStore = useFlowStore()

    const isOpen = ref(false)
    const loading = ref(false)
    const hidden = ref(false)
    const original = ref<string>()
    const edited = ref<string>()

    const tooltip = computed(() => props.edit.editedBy
        ? t("replayEditedTask.recorded_by", {user: props.edit.editedBy, date: dateFilter(props.edit.editedAt)})
        : t("replayEditedTask.recorded_on", {date: dateFilter(props.edit.editedAt)}))

    async function open() {
        isOpen.value = true
        if (edited.value !== undefined || hidden.value) return
        loading.value = true
        try {
            const [flow, definition] = await Promise.all([
                flowStore.loadFlow({namespace: props.namespace, id: props.flowId, revision: String(props.revision), store: false}),
                executionsStore.loadTaskRunEditedDefinition({executionId: props.executionId, taskRunId: props.taskRunId}),
            ])
            original.value = extractTaskDefinition(flow.source, props.taskId)
            edited.value = definition
        } catch (error) {
            if (asProblem(error)?.status !== 403) throw error
            handled(error)
            hidden.value = true
        } finally {
            loading.value = false
        }
    }
</script>

<style scoped>
.edited-tag {
    cursor: pointer;
}

.edited-definition {
    min-height: var(--ks-spacing-16);
}
</style>
