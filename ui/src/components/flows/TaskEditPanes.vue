<template>
    <KsTabs paneScroll :modelValue="activeTab" @update:modelValue="(v) => emit('update:activeTab', v ?? '')">
        <KsTabPane v-if="!readOnly" name="form">
            <template #label>
                <span>{{ $t("form") }}</span>
            </template>
            <div class="task-editor-pane">
                <TaskEditor
                    :modelValue="modelValue"
                    :section="section"
                    :hideRunButton="hideRunButton"
                    @update:model-value="(v) => emit('input', v ?? '')"
                />
            </div>
        </KsTabPane>
        <KsTabPane name="source">
            <template #label>
                <span>{{ $t("source") }}</span>
            </template>
            <KsEditor
                v-bind="editorBindings"
                :readOnly="readOnly"
                :modelValue="modelValue"
                :schemaType="section.toLowerCase()"
                :path="editorPath"
                :options="{fullHeight: false}"
                :navbar="false"
                lang="yaml"
                @save="emit('save')"
                @update:model-value="(v) => emit('input', v ?? '')"
            />
        </KsTabPane>
        <KsTabPane v-if="pluginMarkdown" name="documentation">
            <template #label>
                <span>{{ $t("documentation.documentation") }}</span>
            </template>
            <div class="documentation">
                <KsMarkdown :content="pluginMarkdown" />
            </div>
        </KsTabPane>
    </KsTabs>
</template>

<script setup lang="ts">
    import {KsMarkdown, KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import TaskEditor from "../no-code/components/TaskEditor.vue"

    withDefaults(defineProps<{
        modelValue: string
        section: string
        activeTab: string
        readOnly?: boolean
        pluginMarkdown?: string | null
        hideRunButton?: boolean
        editorPath?: string
    }>(), {
        readOnly: false,
        pluginMarkdown: null,
        hideRunButton: false,
        editorPath: undefined,
    })

    const emit = defineEmits<{
        "input": [value: string | Record<string, any>]
        "update:activeTab": [value: string]
        "save": []
    }>()

    const editorBindings = useEditorBindings()
</script>

<style scoped lang="scss">
    .documentation {
        padding: var(--ks-spacing-4);
    }

    .task-editor-pane {
        padding-left: var(--ks-spacing-5);
        padding-right: var(--ks-spacing-5);
    }
</style>
