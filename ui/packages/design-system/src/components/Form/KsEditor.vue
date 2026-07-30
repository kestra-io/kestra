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

<script lang="ts">
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api"
    import {pebbleBlockKeyAtOffset} from "../../utils/pebbleBlock"
    import {configureMonacoTypescript, registerMonacoThemes} from "../../utils/monacoSetup"

    function isOffsetInPebbleBlock(text: string, offset: number): boolean {
        if (offset < 2) return false
        const searchUpTo = offset - 1
        return text.lastIndexOf("{{", searchUpTo) > text.lastIndexOf("}}", searchUpTo)
    }

    function isCursorInPebbleBlock(editor: monaco.editor.ICodeEditor) {
        const cursorPos = editor.getPosition()
        if (!cursorPos) return false
        const absoluteOffset = editor.getModel()?.getOffsetAt(cursorPos) ?? 0
        return isOffsetInPebbleBlock(editor.getValue(), absoluteOffset)
    }

    function cursorPebbleBlockKey(editor: monaco.editor.ICodeEditor): number | null {
        const cursorPos = editor.getPosition()
        if (!cursorPos) return null
        const absoluteOffset = editor.getModel()?.getOffsetAt(cursorPos) ?? 0
        return pebbleBlockKeyAtOffset(editor.getValue(), absoluteOffset)
    }

    export type {EditorOptions, KsEditorOptions, KsEditorSchemaType, KsEditorExposes} from "../../utils/editorTypes"

    registerMonacoThemes()
    configureMonacoTypescript()
</script>

<script setup lang="ts">
    import "monaco-editor/esm/vs/editor/editor.all"
    import "monaco-editor/esm/vs/editor/standalone/browser/inspectTokens/inspectTokens"
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard"
    import "monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess"
    import "monaco-editor/esm/vs/language/json/monaco.contribution"
    import "monaco-editor/esm/vs/language/typescript/monaco.contribution"
    import "monaco-editor/esm/vs/basic-languages/monaco.contribution"

    import {computed, onBeforeUnmount, onMounted, ref, shallowRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useStorage, useThrottleFn} from "@vueuse/core"
    import {APP_FONT_SIZE_KEY, BASE_PX, type AppFontSizeMode} from "../../utils/fontScale"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
    // @ts-expect-error tab focus path lacks types
    import {TabFocus} from "monaco-editor/esm/vs/editor/browser/config/tabFocus"
    import {editor as monacoEditorNs} from "monaco-editor/esm/vs/editor/editor.api"
    import debounce from "lodash/debounce"

    import type {EditorOptions, KsEditorOptions, KsEditorSchemaType, KsEditorExposes} from "../../utils/editorTypes"
    import {editorModelUid as uid, getOrCreateOverflowWidgetsDomNode} from "../../utils/monacoSetup"
    import KsDatePicker from "./KsDatePicker.vue"
    import {useTaskIcon, type TaskIconProps} from "../../composables/taskIcon"
    import KsButton from "../Basic/KsButton/KsButton.vue"
    import KsButtonGroup from "../Basic/KsButton/KsButtonGroup.vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {findDuplicateTaskIds} from "../../utils/yamlValidation"
    import {createPebbleEntryTracker, isPebbleEnabled} from "../../utils/pebbleBlock"
    import PlaceholderContentWidget from "../../composables/PlaceholderContentWidget"
    import {useEditorDecorations} from "../../composables/useEditorDecorations"
    import {useEditorContentWidget} from "../../composables/useEditorContentWidget"
    import {useEditorScrollMemory} from "../../composables/useEditorScrollMemory"
    import {useEditorDatePicker} from "../../composables/useEditorDatePicker"
    import {useSuggestWidgetIcons} from "../../composables/useSuggestWidgetIcons"

    type ICodeEditor = monacoEditorNs.ICodeEditor

    defineOptions({name: "KsEditor"})

    const {t} = useI18n()

    const taskIconComponent = useTaskIcon()

    const props = withDefaults(defineProps<{
        modelValue?: string
        original?: string
        lang?: string
        path?: string
        schemaType?: KsEditorSchemaType
        theme?: "dark" | "light" | "vs"
        placeholder?: string | number
        label?: string
        readOnly?: boolean
        inline?: boolean
        navbar?: boolean
        configureLanguage?: (editor: ICodeEditor | undefined, language: string, schemaType?: string) => Promise<void>
        loadTaskIcon?: TaskIconProps["loadIcon"]
        options?: KsEditorOptions
    }>(), {
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

    const DEFAULT_OPTIONS: KsEditorOptions = {
        largeSuggestions: true,
        fullHeight: true,
        customHeight: 7,
        diffSideBySide: true,
        wordWrap: true,
        minimap: false,
        creating: false,
        shouldFocus: false,
        showScroll: false,
        diffOverviewBar: true,
        suggestionsOnFocus: false,
    }

    const mergedOptions = computed<KsEditorOptions>(() => ({
        ...DEFAULT_OPTIONS,
        ...props.options,
    }))

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

    const icon = {
        UnfoldLessHorizontal,
        UnfoldMoreHorizontal,
    } as const

    const storedEditorFontSizeOverride = useStorage<number | null>("editorFontSize", null, localStorage, {
        serializer: {
            read: (v) => {
                if (v === null || v === "null" || v === "") return null
                const n = Number(v)
                return isNaN(n) ? null : n
            },
            write: (v) => (v === null ? "null" : String(v)),
        },
    })
    const storedAppFontSizeMode = useStorage<AppFontSizeMode>(APP_FONT_SIZE_KEY, "medium")
    const resolvedEditorFontSize = computed(
        () => storedEditorFontSizeOverride.value ?? (BASE_PX[storedAppFontSizeMode.value] ?? BASE_PX.medium),
    )

    const editorRef = ref<HTMLDivElement | null>(null)
    const container = ref<HTMLDivElement>()
    const isFocused = ref(false)
    const preventCursorChange = ref(false)
    const localEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | undefined>()
    const localDiffEditor = shallowRef<monaco.editor.IStandaloneDiffEditor | undefined>()
    const resizeObserver = ref<ResizeObserver>()

    let lastTimeout: number | undefined
    let moveCursorCmdDisposable: monaco.IDisposable | undefined
    const disposeCompletions = ref<() => void>()

    const datePickerWrapper = ref<HTMLElement>()
    const datePicker = ref()

    const datePickerApi = useEditorDatePicker({
        wrapper: datePickerWrapper,
        picker: datePicker,
        suggestionsOnFocus: computed(() => mergedOptions.value.suggestionsOnFocus),
    })
    const {selectedDate, shown: datePickerShown} = datePickerApi
    const nowMoment = datePickerApi.startOfToday
    const datePickerCallback = () => {
        const editor = asCodeEditorOrUndefined()
        if (editor) datePickerApi.insertSelectedDate(editor)
    }

    const suggestWidgetIcons = useSuggestWidgetIcons({
        taskIcon: taskIconComponent,
        loadTaskIcon: computed(() => props.loadTaskIcon),
        largeSuggestions: computed(() => mergedOptions.value.largeSuggestions),
        codeEditor: () => asCodeEditorOrUndefined(),
        datePicker: datePickerApi,
    })
    const observeAndResizeSuggestWidget = suggestWidgetIcons.observeAndResize

    const decorationsApi = useEditorDecorations({
        pebbleEnabled: computed(() => pebbleEnabled.value),
        highlightLine: computed(() => mergedOptions.value.highlightLine),
        initialHighlight: computed(() => mergedOptions.value.initialHighlight),
        codeEditor: () => isCodeEditor(localEditor.value) ? localEditor.value : undefined,
        modifiedEditor: () => getModifiedEditor() as monaco.editor.IStandaloneCodeEditor | undefined,
    })
    const {highlightPebble, highlightLinesRange, clearLinesRangeHighlights, highlightInitial} = decorationsApi

    const {showWidgetContent, addContentWidget, removeContentWidget} = useEditorContentWidget({
        codeEditor: () => isCodeEditor(localEditor.value) ? localEditor.value : undefined,
        editorRoot: editorRef,
    })

    const scrollMemory = useEditorScrollMemory(computed(() => mergedOptions.value.scrollKey))
    const loadScrollData = scrollMemory.load
    const saveScrollData = scrollMemory.save

    const isDiff = computed(() => props.original !== undefined)

    const editorResolved = computed(() => isDiff.value ? localDiffEditor.value : localEditor.value)

    function asCodeEditorOrUndefined(): monaco.editor.ICodeEditor | undefined {
        const resolved = editorResolved.value
        return resolved?.getEditorType() === monaco.editor.EditorType.ICodeEditor
            ? resolved as monaco.editor.ICodeEditor
            : undefined
    }

    const prefix = computed(() => props.schemaType ? `${props.schemaType}-` : "")

    const isFlowYamlEditor = computed(() => props.lang === "yaml" && props.schemaType === "flow")

    const pebbleEnabled = computed(() => isPebbleEnabled({
        pebble: mergedOptions.value.pebble,
        lang: props.lang,
        schemaType: props.schemaType,
    }))

    const duplicateTaskIdsEnabled = computed(() => {
        if (mergedOptions.value.duplicateTaskIdMarkers !== undefined) return mergedOptions.value.duplicateTaskIdMarkers
        return props.schemaType === "flow" && props.lang === "yaml"
    })

    const containerClass = computed(() => [
        !props.inline ? "" : "single-line",
        "theme-" + props.theme,
        props.theme === "dark" ? "custom-dark-vs-theme" : "",
    ])

    const showPlaceholder = computed(() =>
        props.inline === true &&
        !props.placeholder &&
        !mergedOptions.value.shouldFocus &&
        (!props.modelValue || (typeof props.modelValue === "string" && props.modelValue.trim() === "")) &&
        !isFocused.value,
    )

    const textAreaValue = computed({
        get() {
            return props.modelValue
        },
        set(value) {
            emit("update:modelValue", value)
        },
    })

    const editorOptions = computed<EditorOptions>(() => {
        const opts: EditorOptions = {}

        if (props.inline && !mergedOptions.value.lineNumbers) {
            opts.lineNumbers = "off"
            opts.folding = false
            opts.renderLineHighlight = "none"
            opts.wordBasedSuggestions = "off"
            opts.occurrencesHighlight = "off"
            opts.hideCursorInOverviewRuler = true
            opts.overviewRulerBorder = false
            opts.overviewRulerLanes = 0
            opts.lineNumbersMinChars = 0
            opts.fontSize = 13
            opts.minimap = {enabled: false}
            opts.scrollBeyondLastColumn = 0
            opts.scrollbar = {
                vertical: !mergedOptions.value.showScroll ? "hidden" : "visible",
                horizontal: "hidden",
                alwaysConsumeMouseWheel: false,
                handleMouseWheel: true,
                horizontalScrollbarSize: 0,
                verticalScrollbarSize: !mergedOptions.value.showScroll ? 0 : 5,
                useShadows: false,
            }
            opts.stickyScroll = {enabled: false}
            opts.find = {
                addExtraSpaceOnTop: false,
                autoFindInSelection: "never",
                seedSearchStringFromSelection: "never",
            }
            opts.contextmenu = false
            opts.lineDecorationsWidth = 0
        } else {
            opts.scrollbar = {
                vertical: isDiff.value ? "hidden" : "auto",
                verticalScrollbarSize: isDiff.value ? 0 : 10,
                alwaysConsumeMouseWheel: false,
            }
            opts.renderSideBySide = mergedOptions.value.diffSideBySide
            opts.useInlineViewWhenSpaceIsLimited = false
            opts.renderOverviewRuler = mergedOptions.value.diffOverviewBar
        }

        opts.minimap = mergedOptions.value.minimap ? undefined : {enabled: false}
        opts.readOnly = props.readOnly
        opts.wordWrap = mergedOptions.value.wordWrap ? "on" : "off"
        opts.automaticLayout = true

        return {
            tabSize: 2,
            fontFamily: localStorage.getItem("editorFontFamily") || "'Source Code Pro', monospace",
            fontSize: resolvedEditorFontSize.value,
            showFoldingControls: "always",
            scrollBeyondLastLine: false,
            roundedSelection: false,
            dropIntoEditor: {enabled: false},
            quickSuggestions: {
                other: true,
                comments: false,
                strings: pebbleEnabled.value || props.lang === "yaml",
            },
            ...opts,
            ...props.options?.editor,
        }
    })

    function isCodeEditor(ed?: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor): ed is monaco.editor.IStandaloneCodeEditor {
        return ed?.getEditorType() === monaco.editor.EditorType.ICodeEditor
    }

    function getModifiedEditor() {
        return isDiff.value ? localDiffEditor.value?.getModifiedEditor() : localEditor.value
    }

    function getOriginalEditor() {
        return isDiff.value ? localDiffEditor.value?.getOriginalEditor() : localEditor.value
    }

    function hasVisibleInlineGhostText(codeEditor: monaco.editor.IStandaloneCodeEditor): boolean {
        return codeEditor.getDomNode()?.querySelector(".ghost-text") !== null
    }

    function isTypeLine(lineContent: string): boolean {
        return /^\s*(?:-\s*)?type\s*:\s*.+\s*$/.test(lineContent)
    }

    async function changeTab(pathOrName: string, valueSupplier: () => Promise<string>, useModelCache = true) {
        let model
        if (props.inline || pathOrName === undefined) {
            model = monaco.editor.createModel(
                await valueSupplier(),
                props.lang,
                monaco.Uri.file(prefix.value + uid() + (props.lang ? `.${props.lang}` : "")),
            )
        } else {
            if (!pathOrName.includes(".") && props.lang) {
                pathOrName = `${pathOrName}.${props.lang}`
            }
            const fileUri = monaco.Uri.file(prefix.value + pathOrName)
            model = monaco.editor.getModel(fileUri)
            if (model === null) {
                model = monaco.editor.createModel(await valueSupplier(), props.lang, fileUri)
            } else if (!useModelCache) {
                model.setValue(await valueSupplier())
            }
        }
        localEditor.value?.setModel(model)
        return model
    }

    function needReload(newValue?: {renderSideBySide?: boolean}, oldValue?: {renderSideBySide?: boolean}) {
        return oldValue?.renderSideBySide !== newValue?.renderSideBySide
    }

    function reload() {
        destroy()
        initMonaco()
    }

    async function initMonaco() {
        const editorOpts: EditorOptions = {
            value: props.modelValue,
            theme: props.theme,
            language: props.lang,
            suggest: {showClasses: false, showWords: false},
            ...(isFlowYamlEditor.value ? {inlineSuggest: {enabled: true}} : {}),
            ...editorOptions.value,
        }

        if (isDiff.value) {
            if (!editorRef.value) return
            localDiffEditor.value = monaco.editor.createDiffEditor(editorRef.value, {
                ...editorOpts,
                ignoreTrimWhitespace: false,
            })
            const originalModel = monaco.editor.createModel(
                props.original ?? "",
                props.lang,
                monaco.Uri.file(prefix.value + uid() + (props.lang ? `.${props.lang}` : "")),
            )
            const modifiedModel = monaco.editor.createModel(
                props.modelValue,
                props.lang,
                monaco.Uri.file(prefix.value + uid() + (props.lang ? `.${props.lang}` : "")),
            )
            localDiffEditor.value.setModel({original: originalModel, modified: modifiedModel})

            let modifiedBackspaceTimeout: number | null = null
            const modifiedEditor = localDiffEditor.value.getModifiedEditor()
            modifiedEditor.onKeyDown((e) => {
                if (e.keyCode === monaco.KeyCode.Backspace) {
                    if (modifiedBackspaceTimeout) clearTimeout(modifiedBackspaceTimeout)
                    if (!isCursorInPebbleBlock(modifiedEditor)) return
                    modifiedBackspaceTimeout = window.setTimeout(() => {
                        modifiedEditor.trigger("keyboard", "editor.action.triggerSuggest", {})
                    }, 250)
                }
            })
        } else {
            monaco.editor.addKeybindingRule({keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Space, command: "editor.action.triggerSuggest"})
            monaco.editor.addKeybindingRule({keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyP, command: "editor.action.quickCommand"})
            monaco.editor.addKeybindingRule({keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.DownArrow, command: "editor.action.fontZoomOut", when: "editorFocus"})
            monaco.editor.addKeybindingRule({keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.UpArrow, command: "editor.action.fontZoomIn", when: "editorFocus"})
            monaco.editor.addKeybindingRule({keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Digit0, command: "editor.action.fontZoomReset", when: "editorFocus"})

            if (!editorRef.value) return
            const overflowWidgetsDomNode = getOrCreateOverflowWidgetsDomNode()
            localEditor.value = monaco.editor.create(editorRef.value, {
                ...editorOpts,
                fixedOverflowWidgets: true,
                overflowWidgetsDomNode,
            })

            if (!moveCursorCmdDisposable) {
                moveCursorCmdDisposable = monaco.editor.registerCommand(
                    "moveCursor",
                    (_acc, args?: {lineNumber: number, column: number}) => {
                        const ed = localEditor.value
                        if (!ed || !args?.lineNumber || !args?.column) return
                        ed.setPosition({lineNumber: args.lineNumber, column: args.column})
                        ed.revealPositionInCenter({lineNumber: args.lineNumber, column: args.column})
                        ed.focus()
                    },
                )
            }

            let localBackspaceTimeout: number | null = null
            let suggestController: {model: {state: 0 | 1 | 2}, cancelSuggestWidget: () => void} | undefined

            localEditor.value.onKeyDown((e) => {
                if (isFlowYamlEditor.value && suggestController?.model.state !== 0
                    && (e.keyCode === monaco.KeyCode.Enter || e.keyCode === monaco.KeyCode.Tab)) {
                    const currentLine = localEditor.value?.getModel()?.getLineContent(localEditor.value.getPosition()?.lineNumber ?? 0) ?? ""
                    if (isTypeLine(currentLine)) {
                        setTimeout(() => {
                            const ed = localEditor.value
                            if (!ed) return
                            const position = ed.getPosition()
                            if (!position) return
                            const acceptedLine = ed.getModel()?.getLineContent(position.lineNumber) ?? ""
                            if (!isTypeLine(acceptedLine)) return
                            ed.trigger("typeAcceptedInsertLine", "editor.action.insertLineAfter", {})
                            ed.trigger("typeAcceptedInlineSuggest", "editor.action.inlineSuggest.trigger", {})
                        }, 0)
                    }
                }

                if (isFlowYamlEditor.value && hasVisibleInlineGhostText(localEditor.value!)) {
                    if (e.keyCode === monaco.KeyCode.Tab) {
                        e.preventDefault(); e.stopPropagation()
                        localEditor.value?.trigger("inlineSuggestCommit", "editor.action.inlineSuggest.commit", {})
                        return
                    }
                    if (e.keyCode === monaco.KeyCode.Enter) {
                        localEditor.value?.trigger("inlineSuggestHide", "editor.action.inlineSuggest.hide", {})
                        return
                    }
                }

                if (isFlowYamlEditor.value && e.keyCode === monaco.KeyCode.Enter) {
                    setTimeout(() => localEditor.value?.trigger("inlineSuggestTrigger", "editor.action.inlineSuggest.trigger", {}), 0)
                }

                if (e.keyCode === monaco.KeyCode.Backspace) {
                    if (localBackspaceTimeout) clearTimeout(localBackspaceTimeout)
                    if (!localEditor.value || !isCursorInPebbleBlock(localEditor.value)) return
                    localBackspaceTimeout = window.setTimeout(() => {
                        localEditor.value?.trigger("keyboard", "editor.action.triggerSuggest", {})
                    }, 250)
                }
            })

            if (mergedOptions.value.suggestionsOnFocus) {
                localEditor.value.onMouseDown(() => {
                    localEditor.value!.trigger("click", "editor.action.triggerSuggest", {})
                })
            }

            if (props.placeholder !== undefined && props.placeholder !== "") {
                new PlaceholderContentWidget(String(props.placeholder), localEditor.value)
            }

            suggestController = localEditor.value.getContribution("editor.contrib.suggestController") as unknown as {
                model: {state: 0 | 1 | 2}, cancelSuggestWidget: () => void
            }

            localEditor.value.onDidChangeModelContent(e => {
                if ((e.isUndoing || e.isRedoing) && suggestController!.model.state !== 0) {
                    suggestController!.cancelSuggestWidget()
                    localEditor.value!.trigger("refreshSuggestionsAfterUndoRedo", "editor.action.triggerSuggest", {})
                }
            })

            const pebbleEntryTracker = createPebbleEntryTracker()
            const triggerSuggestionsOnCursorSettle = debounce(() => {
                if (!localEditor.value) return
                const enteredPebble = pebbleEntryTracker.consumeEntered()
                if (suggestController!.model.state !== 0) {
                    suggestController!.cancelSuggestWidget()
                    localEditor.value.trigger("refreshSuggestionsOnCursorMove", "editor.action.triggerSuggest", {})
                } else if (enteredPebble) {
                    localEditor.value.trigger("triggerSuggestionsInPebbleBlock", "editor.action.triggerSuggest", {})
                }
            }, 300)
            localEditor.value.onDidChangeCursorPosition(() => {
                if (!localEditor.value) return
                pebbleEntryTracker.track(cursorPebbleBlockKey(localEditor.value))
                triggerSuggestionsOnCursorSettle()
            })

            localEditor.value.onMouseMove((e) => emit("mouse-move", e))
            localEditor.value.onMouseLeave((e) => emit("mouse-leave", e))

            if (!props.inline) {
                await changeTab(props.path ?? "", () => Promise.resolve(props.modelValue), false)
            }
        }

        const modEditor = getModifiedEditor()
        modEditor?.onDidChangeModelContent(() => {
            const value = modEditor.getValue()
            if (props.modelValue !== value) emit("update:modelValue", value)
        })

        observeAndResizeSuggestWidget()

        setTimeout(() => monaco.editor.remeasureFonts(), 1)
        document.fonts.ready.then(() => monaco.editor.remeasureFonts())

        editorDidMount(editorResolved.value)
        emit("editorMounted", editorResolved.value)

        resizeObserver.value = new ResizeObserver(() => {
            if (localEditor.value) localEditor.value.layout()
            if (localDiffEditor.value) {
                localDiffEditor.value.getModifiedEditor().layout()
                localDiffEditor.value.getOriginalEditor().layout()
            }
        })
        if (editorRef.value) resizeObserver.value.observe(editorRef.value)

        highlightInitial()
    }

    function editorDidMount(monacoMounted?: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor) {
        const ed = monacoMounted
        if (!ed) return

        const KeyCode = monaco.KeyCode
        const KeyMod = monaco.KeyMod

        decorationsApi.attach(ed)

        if (!isCodeEditor(ed)) return

        const codeEditor = ed

        if (mergedOptions.value.scrollKey) {
            const savedState = loadScrollData<monaco.editor.ICodeEditorViewState>("viewState")
            if (savedState) {
                codeEditor.restoreViewState(savedState)
                codeEditor.revealLineInCenterIfOutsideViewport?.(codeEditor.getPosition()?.lineNumber ?? 1)
            }
            const top = loadScrollData<number>("scrollTop", 0)
            if (typeof top === "number") codeEditor.setScrollTop(top)

            const throttledSave = useThrottleFn(() => {
                saveScrollData(codeEditor.saveViewState(), "viewState")
                saveScrollData(codeEditor.getScrollTop(), "scrollTop")
            }, 100)
            codeEditor.onDidScrollChange?.(throttledSave)
        }

        if (!isDiff.value) {
            ed.onDidBlurEditorWidget?.(() => {
                emit("focusout", isCodeEditor(ed) ? ed.getValue() : undefined)
                isFocused.value = false
            })
            if (mergedOptions.value.shouldFocus) {
                ed.onDidFocusEditorText?.(() => { isFocused.value = true })
                ed.focus()
            }
        }

        if (!props.readOnly) {
            ed.addAction({
                id: "kestra-save",
                label: t("save"),
                keybindings: [KeyMod.CtrlCmd | KeyCode.KeyS],
                contextMenuGroupId: "navigation",
                contextMenuOrder: 1.5,
                run: (e) => emit("save", e.getValue()),
            })
        } else if (props.lang === "json") {
            ed.getAction("editor.action.formatDocument")?.run()
        }

        ed.addAction({
            id: "moveCursor",
            label: "Move cursor",
            run: (e, args?: {lineNumber: number, column: number}) => {
                if (!args?.lineNumber || !args?.column) return
                e.setPosition({lineNumber: args.lineNumber, column: args.column})
                e.revealPositionInCenter({lineNumber: args.lineNumber, column: args.column})
                e.focus()
            },
        })

        ed.addAction({
            id: "kestra-execute",
            label: t("execute flow behaviour"),
            keybindings: [KeyMod.CtrlCmd | KeyCode.KeyE],
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: (e) => emit("execute", e.getValue()),
        })

        ed.addAction({
            id: "confirm",
            label: t("confirm"),
            keybindings: [KeyMod.CtrlCmd | KeyCode.Enter],
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: (e) => emit("confirm", e.getValue()),
        })

        ed.onDidFocusEditorText?.(() => {
            TabFocus.setTabFocusMode(mergedOptions.value.keepFocused === undefined ? props.inline : false)
        })

        if (props.inline) {
            ed.addAction({id: "prevent-ctrl-h", label: "Prevent CTRL + H", keybindings: [KeyMod.CtrlCmd | KeyCode.KeyH], run: () => {}})
            ed.addAction({id: "prevent-f1", label: "Prevent F1", keybindings: [KeyCode.F1], run: () => {}})
            if (!props.readOnly) {
                ed.addAction({id: "prevent-ctrl-f", label: "Prevent CTRL + F", keybindings: [KeyMod.CtrlCmd | KeyCode.KeyF], run: () => {}})
            }
        }

        if (!isDiff.value && props.navbar && mergedOptions.value.fullHeight) {
            ed.addAction({
                id: "fold-multiline",
                label: t("fold_all_multi_lines"),
                keybindings: [KeyCode.F10],
                contextMenuGroupId: "fold",
                contextMenuOrder: 1.5,
                async run(e) {
                    const foldingContrib = e.getContribution("editor.contrib.folding") as any
                    const foldingModel = await foldingContrib?.getFoldingModel()
                    const editorModel = foldingModel.textModel
                    const regions = foldingModel.regions
                    const toToggle = []
                    for (let i = regions.length - 1; i >= 0; i--) {
                        if (regions.isCollapsed(i) === false) {
                            const startLineNumber = regions.getStartLineNumber(i)
                            if (editorModel.getLineContent(startLineNumber).trim().endsWith("|")) {
                                toToggle.push(regions.toRegion(i))
                            }
                        }
                    }
                    foldingModel.toggleCollapseState(toToggle)
                },
            })

            if (localStorage.getItem("autofoldTextEditor") === "true") {
                autoFold(true)
            }
        }

        if (!mergedOptions.value.fullHeight) {
            ed.onDidContentSizeChange((e2) => {
                if (!container.value) return
                container.value.style.height = e2.contentHeight + (mergedOptions.value.customHeight ?? 0) + "px"
            })
        }

        if (!isDiff.value) {
            ed.onDidContentSizeChange(() => highlightPebble())
            ed.onDidChangeCursorPosition?.(() => {
                clearTimeout(lastTimeout)
                if (preventCursorChange.value) {
                    preventCursorChange.value = false
                    return
                }
                if (!isCodeEditor(ed)) return
                const position = ed.getPosition()
                const model = ed.getModel()
                lastTimeout = window.setTimeout(() => {
                    if (!position || !model) return
                    emit("cursor", {position, model})
                    if (mergedOptions.value.scrollKey) saveScrollData(codeEditor.saveViewState(), "viewState")
                }, 100)
                highlightPebble()
            })
        }

        const monacoEl = editorRef.value
        if (monacoEl) {
            ;(monacoEl as any).__setValueInTests = (value: string) => {
                if (!isCodeEditor(ed)) return
                ed?.setValue(value)
            }
        }
    }

    function autoFold(shouldFold?: boolean) {
        if (shouldFold) localEditor.value?.trigger("fold", "fold-multiline", {})
    }

    function unfoldAll() {
        const ed = isDiff.value ? localDiffEditor.value?.getModifiedEditor() : localEditor.value
        ed?.trigger("unfold", "editor.unfoldAll", {})
    }

    function onPlaceholderClick() {
        localEditor.value?.layout()
        localEditor.value?.focus()
    }

    function focus() {
        editorResolved.value?.focus()
    }

    function onDrop(event: DragEvent) {
        const text = event.dataTransfer?.getData("text/plain")
        if (!text || !isCodeEditor(localEditor.value)) return
        const ed = localEditor.value
        const target = ed.getTargetAtClientPoint(event.clientX, event.clientY)
        const position = target?.position ?? ed.getPosition()
        if (!position) return
        ed.executeEdits("drop-insert", [{
            range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
            text,
        }])
        ed.focus()
    }

    function destroy() {
        suggestWidgetIcons.teardown()
        disposeCompletions.value?.()
        resizeObserver.value?.disconnect()
        resizeObserver.value = undefined
        if (localDiffEditor.value) {
            localDiffEditor.value?.dispose()
            localDiffEditor.value?.getModel()?.modified?.dispose()
            localDiffEditor.value?.getModel()?.original?.dispose()
            localDiffEditor.value = undefined
        }
        if (localEditor.value) {
            localEditor.value?.dispose()
            localEditor.value?.getModel()?.dispose()
            localEditor.value = undefined
            moveCursorCmdDisposable?.dispose()
            moveCursorCmdDisposable = undefined
        }
    }

    watch(() => [props.modelValue, props.lang], ([value, newLang], [, oldLang]) => {
        preventCursorChange.value = isCodeEditor(localEditor.value) && localEditor.value?.getValue?.() !== value
        if (newLang === oldLang) return
        if (isDiff.value || !localEditor.value || !isCodeEditor(localEditor.value)) return

        const model = localEditor.value.getModel?.()
        if (!model) return

        let lang = "plaintext"
        if (newLang && typeof newLang === "string" && (newLang as string).trim()) {
            const s = newLang as string
            lang = s.includes("json") ? "json" : s.includes("-") ? s.split("-")[0] : s
        }
        try {
            monaco.editor.setModelLanguage(model, lang)
        } catch (e) {
            console.warn("Failed to set model language", e)
        }
    })

    watch(() => props.modelValue, (newValue) => {
        if (!duplicateTaskIdsEnabled.value) return
        if (!localEditor.value || !isCodeEditor(localEditor.value)) return
        const model = localEditor.value.getModel()
        if (!model) return

        const duplicateMarkers = findDuplicateTaskIds(newValue)
        monaco.editor.setModelMarkers(
            model,
            "duplicate-task-ids",
            duplicateMarkers.map((m) => ({
                startLineNumber: m.startLineNumber,
                startColumn: m.startColumn,
                endLineNumber: m.endLineNumber,
                endColumn: m.endColumn,
                message: m.message,
                severity: monaco.MarkerSeverity.Error,
            })),
        )
    }, {immediate: true})

    watch(() => props.modelValue, (newValue) => {
        if (localEditor.value) {
            const modEditor = getModifiedEditor()
            if (newValue !== modEditor?.getValue()) modEditor?.setValue(newValue)
        }
    })

    watch(() => props.original, (newValue) => {
        if (localEditor.value && isDiff.value) {
            const orig = getOriginalEditor()
            if (newValue !== orig?.getValue()) orig?.setValue(newValue ?? "")
        }
        if (isDiff.value && localDiffEditor.value?.getModel()?.modified?.getValue?.() !== props.modelValue) {
            localDiffEditor.value?.getModel()?.modified?.setValue?.(props.modelValue)
        }
    })

    watch(() => props.path, (newValue, oldValue) => {
        if (newValue !== oldValue) {
            changeTab(newValue ?? "", () => Promise.resolve(props.modelValue))
        }
    })

    watch(() => editorOptions.value, (newValue, oldValue) => {
        if (editorResolved.value && needReload(newValue, oldValue)) {
            reload()
        } else {
            localEditor.value?.updateOptions(newValue ?? {})
        }
    }, {deep: true})

    watch(() => props.theme, (newTheme) => {
        if (editorResolved.value && typeof newTheme === "string") {
            monaco.editor.setTheme(newTheme)
        }
    })

    watch(() => props.original, () => reload())

    onMounted(async () => {
        await document.fonts.ready
        await initMonaco()

        if (props.lang !== undefined && props.configureLanguage) {
            await props.configureLanguage(
                isDiff.value ? undefined : (editorResolved.value as ICodeEditor),
                props.lang,
                props.schemaType,
            )
        }

        ;(window as any).pasteToEditor = (textToPaste: string) => {
            localEditor.value?.executeEdits("", [{
                range: localEditor.value?.getSelection() ?? new monaco.Range(0, 0, 0, 0),
                text: textToPaste,
            }])
        }
        ;(window as any).clearEditor = () => localEditor.value?.getModel()?.setValue("")
        ;(window as any).acceptSuggestion = () =>
            localEditor.value?.trigger("acceptSelectedSuggestion", "acceptSelectedSuggestion", {})
        ;(window as any).nextSuggestion = () =>
            localEditor.value?.trigger("selectNextSuggestion", "selectNextSuggestion", {})
    })

    onBeforeUnmount(() => destroy())

    defineExpose<KsEditorExposes>({
        focus,
        destroy,
        highlightLinesRange,
        clearLinesRangeHighlights,
        addContentWidget,
        removeContentWidget,
        monaco,
        getEditor: () => editorResolved.value,
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
                box-shadow: 0 0 0 1px var(--ks-border-default) inset;

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

            .monaco-list-row[aria-label="_DATE_PICKER_"] {
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
