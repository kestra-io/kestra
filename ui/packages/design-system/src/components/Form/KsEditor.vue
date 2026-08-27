<template>
    <div class="ks-editor edit-flow-editor">
        <nav v-if="!isDiff && navbar" class="top-nav">
            <slot name="nav">
                <div class="text-nowrap">
                    <KsButtonGroup>
                        <KsTooltip :content="$t('Fold content lines')">
                            <KsButton
                                :icon="icon.UnfoldLessHorizontal"
                                @click="autoFold(true)"
                                size="small"
                            />
                        </KsTooltip>
                        <KsTooltip :content="$t('Unfold content lines')">
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
    import * as monaco from "monaco-editor/editor/editor.api"
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

<style lang="scss">
    .highlight-lines {
        background-color: rgba(#3991ff, .2);
    }

    .editor-content-widget-content {
        display: flex;
        align-items: center;
        justify-content: center;

        .kel-button-group {
            display: inline-flex;
        }
    }

    :not(.namespace-defaults, .kel-drawer__body) > .ks-editor {
        flex-direction: column;
        height: 100%;
        z-index: 1001;
    }

    :not(.blueprint-container) .ks-editor {
        z-index: 1;
    }

    .kel-form .ks-editor {
        display: flex;
        width: 100%;
    }

    .ks-editor {
        display: flex;
        overflow: hidden;

        .top-nav {
            background-color: var(--ks-bg-surface);
            padding: 0.5rem;
            border-radius: var(--kel-border-radius-round);
            border-bottom-left-radius: 0;
            border-bottom-right-radius: 0;
        }

        .editor-absolute-container {
            position: absolute;
            top: 8px;
            right: var(--ks-font-size-lg);
            z-index: 10;
            color: var(--ks-text-secondary);
            cursor: pointer;
        }

        .editor-absolute-container > * {
            pointer-events: auto;
        }

        .editor-container {
            display: flex;
            flex-grow: 1;

            &:not(.single-line) .editor-wrapper {
                padding-bottom: 4rem;
            }

            &.single-line {
                min-height: var(--kel-component-size);
                padding: 7px 11px;
                background-color: var(--ks-bg-input);
                border-radius: var(--kel-input-border-radius, var(--kel-border-radius-base));
                transition: var(--kel-transition-box-shadow);
                box-shadow: 0 0 0 1px var(--ks-editor-single-line-border-color, var(--ks-border-default)) inset;

                &.custom-dark-vs-theme {
                    background-color: var(--ks-bg-input);
                }
            }

            .placeholder {
                position: absolute;
                top: -3px;
                overflow: hidden;
                padding-left: inherit;
                padding-right: inherit;
                cursor: text;
                user-select: none;
                color: var(--ks-text-inactive);
            }

            .editor-wrapper {
                min-width: 75%;
                width: 100%;

                .monaco-hover-content {
                    h4 {
                        font-size: var(--ks-font-size-base);
                        font-weight: bold;
                        line-height: var(--kbs-body-line-height);
                    }

                    p {
                        margin-bottom: 0.5rem;

                        &:last-child {
                            display: none;
                        }
                    }

                    *:nth-last-child(2n) {
                        margin-bottom: 0;
                    }
                }
            }

            .bottom-right {
                bottom: 0px;
                right: 0px;

                ul {
                    display: flex;
                    list-style: none;
                    padding: 0;
                    margin: 0;
                }
            }

            .editor-footer-row {
                position: absolute;
                left: 0;
                right: 0;
                bottom: 0;
                z-index: 1100;
                pointer-events: none;
                display: flex;
                justify-content: center;

                > * {
                    pointer-events: auto;
                    width: 100%;
                }
            }
        }
    }

    .custom-dark-vs-theme {
        .monaco-editor,
        .monaco-editor-background {
            outline: none;
            background-color: var(--ks-bg-input);
            --vscode-editor-background: var(--ks-bg-input);
            --vscode-breadcrumb-background: var(--ks-bg-input);
            --vscode-editorGutter-background: var(--ks-bg-input);
        }

        .monaco-editor .margin {
            background-color: var(--ks-bg-input);
            --vscode-editorGutter-background: var(--ks-bg-input);
            --vscode-editorLineNumber-activeForeground: var(--ks-text-secondary);
            --vscode-editorLineNumber-foreground: var(--ks-text-secondary);
            --vscode-editorLineNumber-rangeHighlightBackground: var(--ks-text-secondary);
        }
    }

    .highlight-text {
        cursor: pointer;
        font-weight: 700;
        box-shadow: 0 19px 44px rgba(157, 29, 236, 0.31);

        html.dark & {
            background-color: rgba(255, 255, 255, 0.2);
        }
    }

    .highlight-pebble {
        color: #977100 !important;

        html.dark & {
            color: #ffca16 !important;
        }
    }

    .disable-text {
        color: var(--ks-text-inactive) !important;
    }

    .monaco-editor .codelens-decoration > a:hover,
    .monaco-editor .codelens-decoration > a:hover .codicon {
        color: var(--ks-text-link) !important;
    }

    .ks-monaco-editor {
        position: absolute;
        width: 100%;
        height: 100%;
        outline: none;
    }

    .main-editor > #flowFileEditorTab .monaco-editor {
        padding: 1rem 0 0 1rem;
    }

    .custom-dark-vs-theme .ks-monaco-editor .sticky-widget {
        background-color: var(--ks-bg-input);
    }

    .monaco-editor {
        .monaco-scrollable-element {
            > .scrollbar {
                .slider {
                    width: 13px !important;
                    background: var(--ks-border-default) !important;
                    border-radius: 8px !important;
                    border: 4px solid var(--ks-bg-base) !important;
                }
            }

            .monaco-list-row[aria-label^="_DATE_PICKER_"] {
                display: none;
            }
        }
    }

    .kestra-icon-wrapper {
        flex-shrink: 0;
        width: 1em;
        height: 1em;
    }
</style>
