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
                    <div data-testid="monaco-editor" class="ks-monaco-editor" ref="editorRef" />
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

    function uid(): string {
        return Math.random().toString(36).slice(2, 11)
    }

    const OVERFLOW_WIDGETS_ID = "ks-monaco-overflow-widgets"
    function getOrCreateOverflowWidgetsDomNode(): HTMLDivElement {
        let node = document.getElementById(OVERFLOW_WIDGETS_ID) as HTMLDivElement | null
        if (!node) {
            node = document.createElement("div")
            node.id = OVERFLOW_WIDGETS_ID
            node.className = "monaco-editor"
            document.body.appendChild(node)
        }
        return node
    }

    export type EditorOptions = monaco.editor.IStandaloneEditorConstructionOptions & {
        renderSideBySide?: boolean
        useInlineViewWhenSpaceIsLimited?: boolean
        renderOverviewRuler?: boolean
    }

    export type KsEditorOptions = {
        keepFocused?: boolean
        largeSuggestions?: boolean
        fullHeight?: boolean
        customHeight?: number
        diffSideBySide?: boolean
        wordWrap?: boolean
        lineNumbers?: boolean
        minimap?: boolean
        creating?: boolean
        shouldFocus?: boolean
        showScroll?: boolean
        diffOverviewBar?: boolean
        scrollKey?: string
        suggestionsOnFocus?: boolean
        pebble?: boolean
        duplicateTaskIdMarkers?: boolean
        highlightLine?: number
        initialHighlight?: string
        editor?: EditorOptions
    }

    export type KsEditorSchemaType = "flow" | "dashboard" | "app" | "testsuites" | "section" | string

    export interface KsEditorExposes {
        focus: () => void
        destroy: () => void
        highlightLinesRange: (range: {start: number, end: number}) => void
        clearLinesRangeHighlights: () => void
        addContentWidget: (widget: {id: string, position: monaco.IPosition, height: number, right: string}) => Promise<void>
        removeContentWidget: (id: string) => void
        monaco: typeof monaco
        getEditor: () => monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor | undefined
    }

    const themes: Record<string, monaco.editor.IStandaloneThemeData> = {
        dark: {
            base: "vs-dark",
            inherit: true,
            rules: [{token: "", background: "161822"}],
            colors: {
                "minimap.background": "#161822",
                "diffEditor.insertedLineBackground": "#029E734D",
            },
        },
        light: {
            base: "vs",
            inherit: true,
            rules: [
                {token: "type", foreground: "#8405FF"},
                {token: "string.yaml", foreground: "#001233"},
                {token: "comment", foreground: "#8d99ae", fontStyle: "italic"},
            ],
            colors: {
                "editor.lineHighlightBackground": "#fbfaff",
                "editorLineNumber.foreground": "#444444",
                "editor.selectionBackground": "#E8E5FF",
                "editor.wordHighlightBackground": "#E8E5FF",
                "diffEditor.insertedLineBackground": "#029E734D",
            },
        },
    }

    Object.entries(themes).forEach(([themeKey, themeData]) => {
        monaco.editor.defineTheme(themeKey, themeData)
    })

    if (monaco.languages.typescript) {
        monaco.languages.typescript.typescriptDefaults.setCompilerOptions({
            target: monaco.languages.typescript.ScriptTarget.ES2020,
            lib: ["es2020"],
            allowNonTsExtensions: true,
        })
    }
</script>

<script setup lang="ts">
    import "monaco-editor/esm/vs/editor/editor.all"
    import "monaco-editor/esm/vs/editor/standalone/browser/inspectTokens/inspectTokens"
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard"
    import "monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess"
    import "monaco-editor/esm/vs/language/json/monaco.contribution"
    import "monaco-editor/esm/vs/basic-languages/monaco.contribution"

    import type {VNode} from "vue"
    import {computed, h, onBeforeUnmount, onMounted, ref, render, shallowRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useThrottleFn} from "@vueuse/core"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
    // @ts-expect-error tab focus path lacks types
    import {TabFocus} from "monaco-editor/esm/vs/editor/browser/config/tabFocus"
    import {editor as monacoEditorNs} from "monaco-editor/esm/vs/editor/editor.api"
    import moment from "moment"
    import type {Moment} from "moment"
    import debounce from "lodash/debounce"
    import uniqBy from "lodash/uniqBy"

    import KsDatePicker from "./KsDatePicker.vue"
    import KsTaskIcon from "../Kestra/KsTaskIcon.vue"
    import KsButton from "../Basic/KsButton/KsButton.vue"
    import KsButtonGroup from "../Basic/KsButton/KsButtonGroup.vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {STATES} from "../../utils/state"
    import {findDuplicateTaskIds} from "../../utils/yamlValidation"
    import {isPebbleEnabled} from "../../utils/pebbleBlock"
    import PlaceholderContentWidget from "../../composables/PlaceholderContentWidget"

    type ICodeEditor = monacoEditorNs.ICodeEditor

    defineOptions({name: "KsEditor"})

    const {t} = useI18n()

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
        pluginIcons?: Record<string, {icon: string; flowable: boolean}>
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
        pluginIcons: () => ({}),
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
        UnfoldLessHorizontal: shallowRef(UnfoldLessHorizontal),
        UnfoldMoreHorizontal: shallowRef(UnfoldMoreHorizontal),
    } as const

    const editorRef = ref<HTMLDivElement | null>(null)
    const container = ref<HTMLDivElement>()
    const isFocused = ref(false)
    const preventCursorChange = ref(false)
    const showWidgetContent = ref(false)
    const localEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | undefined>()
    const localDiffEditor = shallowRef<monaco.editor.IStandaloneDiffEditor | undefined>()
    const suggestWidgetResizeObserver = ref<MutationObserver>()
    const suggestWidgetObserver = ref<MutationObserver>()
    const suggestWidget = ref<HTMLElement>()
    const resizeObserver = ref<ResizeObserver>()

    let lastTimeout: number | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    let moveCursorCmdDisposable: monaco.IDisposable | undefined
    const disposeCompletions = ref<() => void>()

    const isDiff = computed(() => props.original !== undefined)

    const editorResolved = computed(() => isDiff.value ? localDiffEditor.value : localEditor.value)

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

        const settingsEditorFontSize = localStorage.getItem("editorFontSize")

        return {
            tabSize: 2,
            fontFamily: localStorage.getItem("editorFontFamily") || "'Source Code Pro', monospace",
            fontSize: settingsEditorFontSize ? parseInt(settingsEditorFontSize) : 12,
            showFoldingControls: "always",
            scrollBeyondLastLine: false,
            roundedSelection: false,
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

    // --- Date picker widget for date: values ---
    const nowMoment: Moment = moment().startOf("day")
    const selectedDate = ref<Date>(nowMoment.toDate())
    const datePickerWrapper = ref<HTMLElement>()
    const datePicker = ref<typeof KsDatePicker>()
    const datePickerShown = ref(false)
    let datePickerWidget: monaco.editor.IContentWidget

    const datePickerCallback = () => {
        if (editorResolved.value?.getEditorType() !== monaco.editor.EditorType.ICodeEditor) return

        const asCodeEditor = editorResolved.value as ICodeEditor
        const model = asCodeEditor.getModel()!
        const position = asCodeEditor.getPosition()!
        const wordAtPosition = model.getWordAtPosition(position)

        asCodeEditor.focus()
        model.pushEditOperations(
            asCodeEditor.getSelections(),
            [{
                range: {
                    startLineNumber: position.lineNumber,
                    startColumn: position.column,
                    endLineNumber: position.lineNumber,
                    endColumn: wordAtPosition?.endColumn ?? position.column,
                },
                text: `${moment(
                    (datePicker.value as any)!.$el.nextElementSibling.querySelector("input").value,
                ).toISOString(true)} `,
                forceMoveMarkers: true,
            }],
            () => null,
        )

        selectedDate.value = nowMoment.toDate()

        if (mergedOptions.value.suggestionsOnFocus) {
            asCodeEditor.trigger("datePickerCallback", "editor.action.triggerSuggest", {})
        }
    }

    function removeDatePicker(codeEditor: ICodeEditor) {
        if (!datePickerShown.value) return
        datePickerShown.value = false
        codeEditor.removeContentWidget(datePickerWidget)
    }

    // --- Suggest widget icon swap ---
    const KESTRA_ICON_WRAPPER_CLASS = "kestra-icon-wrapper"
    function replaceRowIcon(vsCodeIcon: HTMLElement, iconVNode: VNode) {
        vsCodeIcon.style.display = "none"
        const tempContainer = document.createElement("div")
        render(h("div", {class: `${KESTRA_ICON_WRAPPER_CLASS} d-flex align-items-center me-1`}, iconVNode), tempContainer)
        vsCodeIcon.after(tempContainer.firstElementChild!)
        tempContainer.remove()
    }

    function replaceRowsIcons(nodes: HTMLElement[]) {
        nodes = uniqBy(nodes, node => node.id)
        for (const node of nodes) {
            const completionValue = node?.getAttribute("aria-label")
            if (!completionValue || node.getAttribute("data-index") === null) continue

            const vsCodeIcon = node.querySelector(".suggest-icon") as HTMLElement
            node.querySelector(`.${KESTRA_ICON_WRAPPER_CLASS}`)?.remove()

            if (completionValue.includes(".") && !completionValue.includes("{")) {
                if (props.pluginIcons?.[completionValue] !== undefined) {
                    replaceRowIcon(vsCodeIcon, h(KsTaskIcon as any, {
                        cls: completionValue,
                        "only-icon": true,
                        icons: props.pluginIcons,
                    }))
                }
            } else if ((STATES as any)[completionValue] !== undefined) {
                replaceRowIcon(vsCodeIcon, h((STATES as any)[completionValue].icon))
            } else {
                vsCodeIcon.style.display = ""
            }
        }
    }

    function addedSuggestRows(mutations: MutationRecord[]) {
        return mutations.flatMap(({addedNodes}) => {
            const nodes = [...addedNodes]
            const maybeRows = nodes.filter((n) => (n as HTMLElement).classList?.contains("monaco-list-row"))
            for (const node of nodes) {
                let maybeRow: Element | null = null
                if (node instanceof Text) {
                    maybeRow = node.parentElement?.closest(".monaco-list-row") ?? null
                }
                if (maybeRow !== null) return [...maybeRows, maybeRow]
            }
            return maybeRows
        }) as HTMLElement[]
    }

    watch(() => props.pluginIcons, () => {
        const widget = suggestWidget.value
        if (widget === undefined) return
        const rows = [...widget.getElementsByClassName("monaco-list-row")] as HTMLElement[]
        if (rows.length > 0) replaceRowsIcons(rows)
    })

    watch(suggestWidget, async (newVal) => {
        const asCodeEditor = editorResolved.value?.getEditorType() === monaco.editor.EditorType.ICodeEditor
            ? editorResolved.value as ICodeEditor : undefined

        if (newVal === undefined) return

        if (newVal.querySelector(".monaco-list-row") !== null) {
            replaceRowsIcons([...newVal.getElementsByClassName("monaco-list-row")] as HTMLElement[])
        }

        suggestWidgetObserver.value?.disconnect()
        suggestWidgetObserver.value = undefined

        suggestWidgetObserver.value = new MutationObserver(mutations => {
            mutations.forEach(({removedNodes}) => {
                if ([...removedNodes.values()].some(n => n instanceof Text && n.textContent === "_DATE_PICKER_")) {
                    if (asCodeEditor !== undefined) removeDatePicker(asCodeEditor)
                }
            })

            const addedRows = addedSuggestRows(mutations)
            replaceRowsIcons(addedRows.filter(row => row.ariaLabel !== "_DATE_PICKER_"))

            addedRows.forEach(async row => {
                if (asCodeEditor !== undefined && row.ariaLabel === "_DATE_PICKER_") {
                    (asCodeEditor.getContribution("editor.contrib.suggestController") as unknown as {
                        cancelSuggestWidget: () => void
                    }).cancelSuggestWidget()

                    if (!datePickerShown.value) {
                        datePickerShown.value = true
                        if (datePickerWidget === undefined) {
                            datePickerWidget = {
                                allowEditorOverflow: true,
                                getId() { return "kestra_date_picker" },
                                getDomNode() { return datePickerWrapper.value! },
                                getPosition() {
                                    return {
                                        position: asCodeEditor.getPosition(),
                                        preference: [
                                            monaco.editor.ContentWidgetPositionPreference.BELOW,
                                            monaco.editor.ContentWidgetPositionPreference.ABOVE,
                                        ],
                                    }
                                },
                            }
                        }
                        await asCodeEditor.addContentWidget(datePickerWidget)
                        ;(datePicker.value as any)!.handleOpen()
                        setTimeout(() => (datePicker.value as any)!.focus())
                    }
                }
            })
        })

        suggestWidgetObserver.value.observe(newVal, {childList: true, subtree: true})

        asCodeEditor?.onDidChangeCursorPosition(() => removeDatePicker(asCodeEditor))
    })

    // --- Suggest widget auto-resize (large suggestions) ---
    function observeAndResizeSuggestWidget() {
        if (suggestWidgetResizeObserver.value !== undefined) return

        suggestWidgetResizeObserver.value = new MutationObserver(([{target, addedNodes}]) => {
            const simulateResizeOnSashAndDisconnect = (resizer: HTMLElement) => {
                if (!mergedOptions.value.largeSuggestions) return

                suggestWidgetResizeObserver.value?.disconnect()
                suggestWidgetResizeObserver.value = undefined

                const r = {x: resizer.getBoundingClientRect().left, y: resizer.getBoundingClientRect().top}
                const fire = (type: string, dx = 0) => resizer.dispatchEvent(new MouseEvent(type, {bubbles: true, clientX: r.x + dx, clientY: r.y}))
                fire("mouseenter"); fire("mouseover"); fire("mousedown")
                fire("mousemove", 80); fire("mouseup", 80); fire("mouseout", 80); fire("mouseleave", 80)
            }

            const targetHtmlElement = target as HTMLElement
            if (targetHtmlElement.classList.contains("monaco-sash")) {
                if (!targetHtmlElement.classList.contains("disabled")) {
                    simulateResizeOnSashAndDisconnect(targetHtmlElement)
                }
                return
            }

            const maybeSuggestWidgetHtmlElement = addedNodes?.[0] as HTMLElement
            if (maybeSuggestWidgetHtmlElement?.classList.contains("suggest-widget")) {
                suggestWidget.value = maybeSuggestWidgetHtmlElement
                const resizer = maybeSuggestWidgetHtmlElement.querySelector(".monaco-sash.vertical") as HTMLElement
                if (resizer.classList.contains("disabled")) {
                    suggestWidgetResizeObserver.value!.disconnect()
                    suggestWidgetResizeObserver.value?.observe(resizer, {attributeFilter: ["class"]})
                } else {
                    simulateResizeOnSashAndDisconnect(resizer)
                }
            }
        })

        // Monaco renders overflow widgets (including suggest-widget) in the shared
        // #ks-monaco-overflow-widgets node appended to document.body (fixedOverflowWidgets: true).
        // Querying editorRef for .overflowingContentWidgets returns null.
        const overflowNode = document.getElementById(OVERFLOW_WIDGETS_ID)
        const target = overflowNode?.querySelector(".overflowingContentWidgets")
            ?? overflowNode
        if (target) suggestWidgetResizeObserver.value.observe(target, {childList: true})
    }

    // --- initial highlight (deep-link via prop) ---
    function highlightInitial() {
        const initialHighlight = mergedOptions.value.initialHighlight
        if (!initialHighlight) return
        const ed = getModifiedEditor()
        if (!ed) return

        ed.focus()
        const lines = ed.getModel()!.getLinesContent()
        let lineNumber = 0
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes(initialHighlight)) {
                lineNumber = i + 1
                break
            }
        }
        const endLineCharacter = ed.getModel()!.getLineMaxColumn(lineNumber) ?? 0
        ed.setSelection(new monaco.Range(lineNumber, 0, lineNumber, endLineCharacter))
        ed.revealLineInCenter(lineNumber)
    }

    watch(() => mergedOptions.value.highlightLine, (line) => {
        if (!line) return
        const ed = getModifiedEditor()
        if (!ed) return
        ed.focus()
        const end = ed.getModel()?.getLineMaxColumn(line) ?? 0
        ed.setSelection(new monaco.Range(line, 0, line, end))
    })

    // --- model lifecycle ---
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

    // --- Pebble highlight ---
    const decorationsLists: {
        pebble?: monaco.editor.IModelDeltaDecoration[]
        lines?: monaco.editor.IModelDeltaDecoration[]
    } = {}

    function setDecorations() {
        decorations?.clear()
        if (decorationsLists.lines) decorations?.append(decorationsLists.lines)
        if (decorationsLists.pebble) decorations?.append(decorationsLists.pebble)
    }

    function highlightPebble() {
        if (!pebbleEnabled.value) {
            decorationsLists.pebble = []
            setDecorations()
            return
        }
        if (!isCodeEditor(localEditor.value)) return
        const model = localEditor.value?.getModel?.()
        const text = model?.getValue?.()
        const regex = new RegExp("\\{\\{(.+?)}}", "g")
        let match
        const decorationsToAdd: monaco.editor.IModelDeltaDecoration[] = []
        if (text && model) while ((match = regex.exec(text)) !== null) {
            const startPos = model.getPositionAt(match.index)
            const endPos = model.getPositionAt(match.index + match[0].length)
            decorationsToAdd.push({
                range: {
                    startLineNumber: startPos.lineNumber,
                    startColumn: startPos.column,
                    endLineNumber: endPos.lineNumber,
                    endColumn: endPos.column,
                },
                options: {inlineClassName: "highlight-pebble"},
            })
        }
        decorationsLists.pebble = decorationsToAdd
        setDecorations()
    }

    function getHighlightDecoration(range: {start: number, end: number}) {
        return [{
            range: new monaco.Range(range.start, 1, range.end, 1),
            options: {isWholeLine: true, className: "highlight-lines"},
        }] as monaco.editor.IModelDeltaDecoration[]
    }

    function highlightLinesRange(range: {start: number, end: number}) {
        decorationsLists.lines = getHighlightDecoration(range)
        setDecorations()
    }

    function clearLinesRangeHighlights() {
        decorationsLists.lines = []
        setDecorations()
    }

    // --- Content widget API (date picker / playground button host) ---
    const widgetNode = (() => {
        const node = document.createElement("div")
        node.className = "editor-content-widget"
        const content = document.createElement("div")
        content.className = "editor-content-widget-content"
        node.appendChild(content)
        return node
    })()

    async function wait(time: number) {
        return new Promise(resolve => setTimeout(resolve, time))
    }

    async function waitForWidgetContentNode() {
        await wait(30)
        if (document.querySelector(".editor-content-widget-content") === null) {
            return waitForWidgetContentNode()
        }
    }

    async function addContentWidget(widget: {id: string, position: monaco.IPosition, height: number, right: string}) {
        if (!isCodeEditor(localEditor.value)) return
        localEditor.value?.addContentWidget({
            getId() { return widget.id },
            getPosition() {
                return {
                    position: widget.position,
                    preference: [monaco.editor.ContentWidgetPositionPreference.EXACT],
                }
            },
            getDomNode: () => {
                const content = widgetNode.querySelector(".editor-content-widget-content") as HTMLDivElement
                if (content) content.style.height = widget.height + "rem"
                return widgetNode
            },
            afterRender() {
                const rect = editorRef.value!.querySelector(".monaco-scrollable-element")!.getBoundingClientRect()
                widgetNode.style.left = `calc(${rect.width}px - 150px - ${widget.right})`
            },
        })

        await waitForWidgetContentNode()
        showWidgetContent.value = true
    }

    function removeContentWidget(id: string) {
        showWidgetContent.value = false
        if (!isCodeEditor(localEditor.value)) return
        localEditor.value?.removeContentWidget({
            getId: () => id,
            getPosition() { return {position: {lineNumber: 0, column: 0}, preference: []} },
            getDomNode: () => widgetNode,
        })
    }

    // --- Scroll memory (simple localStorage backed) ---
    function loadScrollData<T>(key: string, fallback?: T): T | undefined {
        const scrollKey = mergedOptions.value.scrollKey
        if (!scrollKey) return fallback
        try {
            const raw = localStorage.getItem(`editorScroll:${scrollKey}:${key}`)
            return raw ? (JSON.parse(raw) as T) : fallback
        } catch {
            return fallback
        }
    }
    function saveScrollData<T>(data: T, key: string) {
        const scrollKey = mergedOptions.value.scrollKey
        if (!scrollKey) return
        try {
            localStorage.setItem(`editorScroll:${scrollKey}:${key}`, JSON.stringify(data))
        } catch {
            // ignore
        }
    }

    // --- Editor lifecycle ---
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
                // oxlint-disable-next-line no-new
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

            let wasInPebbleBlock = false
            localEditor.value.onDidChangeCursorPosition(debounce(() => {
                if (!localEditor.value) return
                const inPebble = isCursorInPebbleBlock(localEditor.value)
                if (suggestController!.model.state !== 0) {
                    suggestController!.cancelSuggestWidget()
                    localEditor.value.trigger("refreshSuggestionsOnCursorMove", "editor.action.triggerSuggest", {})
                } else if (inPebble && !wasInPebbleBlock) {
                    localEditor.value.trigger("triggerSuggestionsInPebbleBlock", "editor.action.triggerSuggest", {})
                }
                wasInPebbleBlock = inPebble
            }, 300))

            localEditor.value.onMouseMove((e) => emit("mouse-move", e))
            localEditor.value.onMouseLeave((e) => emit("mouse-leave", e))

            if (!props.inline) {
                await changeTab(props.path ?? "", () => Promise.resolve(props.modelValue), false)
            }
        }

        // Editor change → emit update:modelValue
        const modEditor = getModifiedEditor()
        modEditor?.onDidChangeModelContent(() => {
            const value = modEditor.getValue()
            if (props.modelValue !== value) emit("update:modelValue", value)
        })

        observeAndResizeSuggestWidget()

        setTimeout(() => monaco.editor.remeasureFonts(), 1)

        // Editor-did-mount: setup keybindings + decorations
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

        decorations = ed.createDecorationsCollection()

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

        // e2e contract: expose imperative setValue for Playwright
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

    function destroy() {
        suggestWidgetResizeObserver.value?.disconnect()
        suggestWidgetResizeObserver.value = undefined
        suggestWidget.value = undefined
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

    // --- Watchers from old Editor.vue + MonacoEditor.vue ---
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

    // --- Mount/unmount ---
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

        // e2e contracts (Playwright hooks)
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
