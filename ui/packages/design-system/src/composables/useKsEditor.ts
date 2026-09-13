import "monaco-editor/features/register.all"
import "monaco-editor/editor/standalone/browser/inspectTokens/inspectTokens"
import "monaco-editor/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard"
import "monaco-editor/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess"
import "monaco-editor/language/json/monaco.contribution"
import "monaco-editor/language/typescript/monaco.contribution"
import "monaco-editor/basic-languages/monaco.contribution"
import {computed, onBeforeUnmount, onMounted, ref, shallowRef, watch} from "vue"
import {useI18n} from "vue-i18n"
import {useStorage} from "@vueuse/core"
import {APP_FONT_SIZE_KEY, MONO_BASE_PX, type AppFontSizeMode} from "../utils/fontScale"
import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
// @ts-expect-error tab focus path lacks types
import {TabFocus} from "monaco-editor/editor/browser/config/tabFocus"
import * as monaco from "monaco-editor/editor/editor.api"
import {editor as monacoEditorNs} from "monaco-editor/editor/editor.api"
import debounce from "lodash/debounce"
import type {EditorOptions, KsEditorOptions} from "../utils/editorTypes"
import {configureMonacoTypescript, editorModelUid as uid, getOrCreateOverflowWidgetsDomNode, registerMonacoThemes} from "../utils/monacoSetup"
import {useTaskIcon} from "./taskIcon"
import {findDuplicateTaskIds} from "../utils/yamlValidation"
import {createPebbleEntryTracker, cursorPebbleBlockKey, isCursorInPebbleBlock, isPebbleEnabled} from "../utils/pebbleBlock"
import PlaceholderContentWidget from "./PlaceholderContentWidget"
import {useEditorDecorations} from "./useEditorDecorations"
import {useEditorContentWidget} from "./useEditorContentWidget"
import {useEditorScrollMemory} from "./useEditorScrollMemory"
import {useEditorDatePicker} from "./useEditorDatePicker"
import {useSuggestWidgetIcons} from "./useSuggestWidgetIcons"
import {registerEditorActions} from "./useEditorActions"
import type {KsEditorEmit, KsEditorTemplateRefs, ResolvedKsEditorProps} from "../utils/editorTypes"
type ICodeEditor = monacoEditorNs.ICodeEditor

registerMonacoThemes()
configureMonacoTypescript()

export function useKsEditor(
    props: ResolvedKsEditorProps,
    emit: KsEditorEmit,
    refs: KsEditorTemplateRefs,
) {
    const {editorRef, container, datePickerWrapper, datePicker} = refs
    const {t} = useI18n()

    const taskIconComponent = useTaskIcon()


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
        () => storedEditorFontSizeOverride.value ?? (MONO_BASE_PX[storedAppFontSizeMode.value] ?? MONO_BASE_PX.medium),
    )

    const isFocused = ref(false)
    const preventCursorChange = ref(false)
    const localEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | undefined>()
    const localDiffEditor = shallowRef<monaco.editor.IStandaloneDiffEditor | undefined>()
    const resizeObserver = ref<ResizeObserver>()

    let lastTimeout: number | undefined
    let moveCursorCmdDisposable: monaco.IDisposable | undefined
    const disposeCompletions = ref<() => void>()


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
            // monaco-editor 0.56 defaults to the native EditContext API instead of a hidden textarea for
            // text input; that surface isn't a real <textarea>/[contenteditable], which breaks Playwright's
            // .fill() and other tooling that expects the old input model. Opt back into it explicitly.
            editContext: false,
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

        decorationsApi.attach(ed)

        if (!isCodeEditor(ed)) return

        const codeEditor = ed

        scrollMemory.restoreAndTrack(codeEditor)

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

        registerEditorActions(ed, {
            t,
            readOnly: props.readOnly,
            inline: props.inline,
            lang: props.lang,
            canFoldFromNavbar: !isDiff.value && props.navbar && Boolean(mergedOptions.value.fullHeight),
            onSave: (value) => emit("save", value),
            onExecute: (value) => emit("execute", value),
            onConfirm: (value) => emit("confirm", value),
            autoFold,
        })

        ed.onDidFocusEditorText?.(() => {
            TabFocus.setTabFocusMode(mergedOptions.value.keepFocused === undefined ? props.inline : false)
        })

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
                    scrollMemory.saveViewState(codeEditor)
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

    function getEditor() {
        return editorResolved.value
    }

    return {
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
    }
}
