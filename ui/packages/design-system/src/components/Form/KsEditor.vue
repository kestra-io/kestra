<template>
    <div class="ks-editor edit-flow-editor">
        <nav v-if="!isDiff && navbar" class="top-nav">
            <slot name="nav">
                <div class="text-nowrap">
                    <KsButtonGroup>
                        <KsTooltip :content="t('Fold content lines')">
                            <KsButton
                                :icon="icon.UnfoldLessHorizontal"
                                @click="autoFold(true)"
                                size="small"
                            />
                        </KsTooltip>
                        <KsTooltip :content="t('Unfold content lines')">
                            <KsButton
                                :icon="icon.UnfoldMoreHorizontal"
                                @click="unfoldAll"
                                size="small"
                            />
                        </KsTooltip>
                    </KsButtonGroup>
                    <slot name="extends-navbar" />
                </div>
            </slot>
        </nav>
        <div class="editor-absolute-container pe-none">
            <slot name="absolute" />
        </div>
        <span v-if="label" class="label">{{ label }}</span>
        <div class="editor-container" ref="container" :class="[containerClass, {'mb-2': label}]">
            <div ref="editorContainer" class="editor-wrapper position-relative">
                <div>
                    <div
                        data-testid="monaco-editor"
                        class="ks-monaco-editor"
                        ref="editorRef"
                        @dragover.prevent
                        @drop.prevent="onDrop"
                    />
                    <div ref="datePickerWrapper" v-show="datePickerShown">
                        <KsDatePicker
                            ref="datePicker"
                            type="datetime"
                            v-model="selectedDate"
                            :teleported="false"
                            :defaultValue="nowMoment.toDate()"
                            @change="datePickerCallback"
                            @keydown.esc.prevent="editorResolved?.focus()"
                            @keydown.enter.prevent="datePickerCallback"
                            :clearable="false"
                            class="z-3"
                        />
                    </div>
                    <textarea
                        data-testid="monaco-editor-hidden-synced-textarea"
                        style="height: 0; width: 0; opacity: 0;"
                        type="text"
                        v-model="textAreaValue"
                    />
                </div>
                <div
                    v-show="showPlaceholder"
                    class="placeholder"
                    @click="onPlaceholderClick"
                >
                    {{ placeholder }}
                </div>
                <div class="position-absolute bottom-right">
                    <slot name="buttons" />
                </div>
                <div class="editor-footer-row">
                    <slot name="footer-row" />
                </div>
            </div>
        </div>

        <Teleport v-if="showWidgetContent" to=".editor-content-widget-content">
            <slot name="widget-content" />
        </Teleport>
    </div>
</template>


<script setup lang="ts">
    import {ref} from "vue"
    import KsDatePicker from "./KsDatePicker.vue"
    import KsButton from "../Basic/KsButton/KsButton.vue"
    import KsButtonGroup from "../Basic/KsButton/KsButtonGroup.vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api"
    import {useKsEditor} from "../../composables/useKsEditor"
    import type {KsEditorExposes, KsEditorProps} from "../../utils/editorTypes"

    defineOptions({name: "KsEditor"})

    const props = withDefaults(defineProps<KsEditorProps>(), {
        modelValue: "",
        original: undefined,
        lang: undefined,
        path: "",
        schemaType: undefined,
        theme: "dark",
        placeholder: "",
        label: undefined,
        readOnly: false,
        inline: false,
        navbar: true,
        configureLanguage: undefined,
        loadTaskIcon: undefined,
        options: undefined,
    })

    const emit = defineEmits<{
        (e: "save", value?: string): void
        (e: "execute", value?: string): void
        (e: "focusout", value?: string): void
        (e: "update:modelValue", value: string): void
        (e: "cursor", payload: {position: monaco.Position, model: monaco.editor.ITextModel}): void
        (e: "confirm", value?: string): void
        (e: "mouse-move", event: monaco.editor.IEditorMouseEvent): void
        (e: "mouse-leave", event: monaco.editor.IPartialEditorMouseEvent): void
        (e: "editorMounted", editor: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor | undefined): void
    }>()

    const editorRef = ref<HTMLDivElement | null>(null)
    const container = ref<HTMLDivElement>()
    const datePickerWrapper = ref<HTMLElement>()
    const datePicker = ref()

    const {
        t,
        icon,
        containerClass,
        showPlaceholder,
        textAreaValue,
        showWidgetContent,
        isDiff,
        editorResolved,
        selectedDate,
        datePickerShown,
        nowMoment,
        datePickerCallback,
        autoFold,
        unfoldAll,
        onPlaceholderClick,
        onDrop,
        focus,
        destroy,
        highlightLinesRange,
        clearLinesRangeHighlights,
        addContentWidget,
        removeContentWidget,
        getEditor,
    } = useKsEditor(props, emit, {editorRef, container, datePickerWrapper, datePicker})

    defineExpose<KsEditorExposes>({
        focus,
        destroy,
        highlightLinesRange,
        clearLinesRangeHighlights,
        addContentWidget,
        removeContentWidget,
        monaco,
        getEditor,
    })
</script>

<style lang="scss" src="./KsEditor.scss" />
