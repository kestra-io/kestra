<template>
    <section v-ks-loading="loading" class="replay-task-editor" data-test="replay-task-editor">
        <KsCheckbox v-model="editing" :disabled="!original" data-test="replay-task-editor-toggle">
            {{ $t("replayEditedTask.toggle", {taskId}) }}
        </KsCheckbox>
        <KsText tag="p" size="small" type="info" class="description">
            {{ $t("replayEditedTask.description") }}
        </KsText>

        <KsAlert
            v-if="loadFailed"
            type="error"
            :closable="false"
            showIcon
            data-test="replay-task-editor-error"
            :title="$t('replayEditedTask.load_error', {taskId})"
        />

        <template v-if="editing && original">
            <KsEditor
                v-bind="editorBindings"
                v-model="text"
                class="definition"
                lang="yaml"
                :navbar="false"
                data-test="replay-task-editor-yaml"
            />
            <KsText v-if="check.error" size="small" type="danger" data-test="replay-task-editor-invalid">
                {{ $t(`replayEditedTask.error.${check.error}`) }}
            </KsText>
        </template>
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue"
    import {useFlowStore} from "../../stores/flow"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {checkDefinition, extractTaskDefinition} from "./replayEditedTask"

    const props = defineProps<{
        namespace: string
        flowId: string
        revision: number
        taskId: string
    }>()

    const definition = defineModel<string | undefined>("definition", {required: true})
    const invalid = defineModel<boolean>("invalid", {default: false})

    const flowStore = useFlowStore()
    const editorBindings = useEditorBindings()

    const loading = ref(true)
    const loadFailed = ref(false)
    const editing = ref(false)
    const original = ref<string>()
    const text = ref("")

    const check = computed(() => original.value ? checkDefinition(original.value, text.value) : {})

    watch([editing, text, check], () => {
        const changed = editing.value && !!original.value && text.value !== original.value
        definition.value = changed && !check.value.error ? text.value : undefined
        invalid.value = changed && !!check.value.error
    }, {immediate: true})

    onMounted(async () => {
        try {
            const flow = await flowStore.loadFlow({namespace: props.namespace, id: props.flowId, revision: String(props.revision), store: false})
            original.value = extractTaskDefinition(flow.source, props.taskId)
            text.value = original.value ?? ""
            loadFailed.value = !original.value
        } catch {
            loadFailed.value = true
        } finally {
            loading.value = false
        }
    })
</script>

<style scoped>
.replay-task-editor {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
    margin-top: var(--ks-spacing-5);
}

.description {
    margin: 0;
}

.ks-editor.definition {
    height: 16rem;
}
</style>
