import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import {useKsEditor} from "./useKsEditor"
import type {KsEditorEmit, KsEditorTemplateRefs, ResolvedKsEditorProps} from "../utils/editorTypes"

const {markerTrigger, onDidChangeMarkers, getModelMarkers, markerDispose} = vi.hoisted(() => {
    let markerCallback: ((uris: {toString(): string}[]) => void) | null = null
    const markerDispose = vi.fn()
    return {
        markerTrigger: (uris: {toString(): string}[]) => markerCallback?.(uris),
        onDidChangeMarkers: vi.fn((cb: (uris: {toString(): string}[]) => void) => {
            markerCallback = cb
            return {dispose: markerDispose}
        }),
        getModelMarkers: vi.fn(() => [] as unknown[]),
        markerDispose,
    }
})

vi.mock("monaco-editor/features/register.all", () => ({}))
vi.mock("monaco-editor/editor/standalone/browser/inspectTokens/inspectTokens", () => ({}))
vi.mock("monaco-editor/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard", () => ({}))
vi.mock("monaco-editor/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess", () => ({}))
vi.mock("monaco-editor/language/json/monaco.contribution", () => ({}))
vi.mock("monaco-editor/language/typescript/monaco.contribution", () => ({}))
vi.mock("monaco-editor/basic-languages/monaco.contribution", () => ({}))
vi.mock("monaco-editor/editor/browser/config/tabFocus", () => ({TabFocus: {setTabFocusMode: vi.fn()}}))

vi.mock("monaco-editor/editor/editor.api", () => ({
    MarkerSeverity: {Warning: 4, Error: 8, Info: 2, Hint: 1},
    KeyCode: {Backspace: 1},
    KeyMod: {CtrlCmd: 0},
    Range: class {
        constructor(public sl: number, public sc: number, public el: number, public ec: number) { }
    },
    Uri: {file: (path: string) => ({toString: () => path})},
    editor: {
        EditorType: {ICodeEditor: "vs.editor.ICodeEditor"},
        onDidChangeMarkers,
        getModelMarkers,
        create: vi.fn(() => ({
            getModel: () => ({
                uri: {toString: () => "inmemory://test.yaml"},
                dispose: vi.fn(),
                getValue: vi.fn(() => ""),
                setValue: vi.fn(),
            }),
            getEditorType: () => "vs.editor.ICodeEditor",
            dispose: vi.fn(),
            setValue: vi.fn(),
            getValue: vi.fn(() => ""),
            getSelection: vi.fn(),
            getContribution: vi.fn(() => ({model: {state: 0}, cancelSuggestWidget: vi.fn()})),
            trigger: vi.fn(),
            focus: vi.fn(),
            setModel: vi.fn(),
            onDidBlurEditorWidget: vi.fn(() => ({dispose: vi.fn()})),
            onDidFocusEditorText: vi.fn(() => ({dispose: vi.fn()})),
            onDidContentSizeChange: vi.fn(() => ({dispose: vi.fn()})),
            onDidChangeCursorPosition: vi.fn(() => ({dispose: vi.fn()})),
            onKeyDown: vi.fn(() => ({dispose: vi.fn()})),
            onMouseDown: vi.fn(() => ({dispose: vi.fn()})),
            onMouseMove: vi.fn(() => ({dispose: vi.fn()})),
            onMouseLeave: vi.fn(() => ({dispose: vi.fn()})),
            onDidChangeModelContent: vi.fn(() => ({dispose: vi.fn()})),
            updateOptions: vi.fn(),
            layout: vi.fn(),
        })),
        createModel: vi.fn(() => ({
            uri: {toString: () => "inmemory://test.yaml"},
            getValue: vi.fn(() => ""),
            setValue: vi.fn(),
            dispose: vi.fn(),
        })),
        createDiffEditor: vi.fn(),
        getModel: vi.fn(() => null),
        setTheme: vi.fn(),
        setModelLanguage: vi.fn(),
        remeasureFonts: vi.fn(),
        addKeybindingRule: vi.fn(),
        registerCommand: vi.fn(() => ({dispose: vi.fn()})),
    },
}))

vi.mock("@vueuse/core", () => ({
    useStorage: vi.fn((_, defaultVal: unknown) => ref(defaultVal)),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock("../utils/monacoSetup", () => ({
    configureMonacoTypescript: vi.fn(),
    editorModelUid: vi.fn(() => "uid"),
    getOrCreateOverflowWidgetsDomNode: vi.fn(() => document.createElement("div")),
    registerMonacoThemes: vi.fn(),
}))

vi.mock("./taskIcon", () => ({
    useTaskIcon: vi.fn(() => ({render: () => null})),
    TASK_ICON_INJECTION_KEY: Symbol(),
}))

vi.mock("./useEditorDecorations", () => ({
    useEditorDecorations: vi.fn(() => ({
        attach: vi.fn(),
        highlightPebble: vi.fn(),
        highlightLinesRange: vi.fn(),
        clearLinesRangeHighlights: vi.fn(),
        highlightInitial: vi.fn(),
    })),
}))

vi.mock("./useEditorContentWidget", () => ({
    useEditorContentWidget: vi.fn(() => ({
        showWidgetContent: ref(false),
        addContentWidget: vi.fn(),
        removeContentWidget: vi.fn(),
    })),
}))

vi.mock("./useEditorScrollMemory", () => ({
    useEditorScrollMemory: vi.fn(() => ({
        restoreAndTrack: vi.fn(),
        saveViewState: vi.fn(),
    })),
}))

vi.mock("./useEditorDatePicker", () => ({
    useEditorDatePicker: vi.fn(() => ({
        selectedDate: ref(null),
        shown: ref(false),
        startOfToday: ref(null),
        insertSelectedDate: vi.fn(),
    })),
}))

vi.mock("./useFlowYamlEditor", () => ({
    useFlowYamlEditor: vi.fn(() => ({
        constructionOptions: ref({}),
        attach: vi.fn(),
    })),
}))

vi.mock("./useSuggestWidgetIcons", () => ({
    useSuggestWidgetIcons: vi.fn(() => ({
        observeAndResize: vi.fn(),
        teardown: vi.fn(),
    })),
}))

vi.mock("./useEditorActions", () => ({
    registerEditorActions: vi.fn(() => ({dispose: vi.fn()})),
}))

vi.mock("../utils/pebbleBlock", () => ({
    createPebbleEntryTracker: vi.fn(() => ({track: vi.fn(), consumeEntered: vi.fn(() => false)})),
    cursorPebbleBlockKey: vi.fn(() => ""),
    isCursorInPebbleBlock: vi.fn(() => false),
    isPebbleEnabled: vi.fn(() => false),
}))

vi.mock("./PlaceholderContentWidget", () => ({
    default: class {
        dispose = vi.fn()
    },
}))

function mountEditor(emit: KsEditorEmit) {
    const props = {
        modelValue: "",
        options: {},
        theme: "vs",
        readOnly: false,
        inline: false,
        navbar: false,
        placeholder: "",
        path: "",
    } as unknown as ResolvedKsEditorProps
    const refs: KsEditorTemplateRefs = {
        editorRef: ref(document.createElement("div")),
        container: ref(document.createElement("div")),
        datePickerWrapper: ref(document.createElement("div")),
        datePicker: ref(null),
    } as unknown as KsEditorTemplateRefs

    const Comp = defineComponent({
        setup() {
            useKsEditor(props, emit, refs)
            return () => h("div")
        },
    })
    return mount(Comp)
}

describe("useKsEditor validation listener", () => {
    let mockEmit: ReturnType<typeof vi.fn>

    beforeEach(() => {
        vi.clearAllMocks()
        mockEmit = vi.fn()
        getModelMarkers.mockReturnValue([])
        Object.defineProperty(document, "fonts", {
            value: {ready: Promise.resolve()},
            configurable: true,
        })
        vi.stubGlobal("ResizeObserver", class {
            observe = vi.fn()
            unobserve = vi.fn()
            disconnect = vi.fn()
        })
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it("filters out backend-validation and errors, emitting only mapped schema warnings", async () => {
        const wrapper = mountEditor(mockEmit as unknown as KsEditorEmit)
        await flushPromises()

        getModelMarkers.mockReturnValue([
            {severity: 4, owner: "yaml-schema", startLineNumber: 10, message: "Missing tasks"},
            {severity: 8, owner: "yaml-schema", startLineNumber: 12, message: "Syntax error"},
            {severity: 4, owner: "backend-validation", startLineNumber: 15, message: "Backend warning"},
        ])

        markerTrigger([{toString: () => "inmemory://test.yaml"}])

        expect(getModelMarkers).toHaveBeenCalled()
        expect(mockEmit).toHaveBeenCalledWith("editor-warnings", [
            {line: 10, message: "Missing tasks"},
        ])

        wrapper.unmount()
    })

    it("emits an empty list when the warnings are gone", async () => {
        const wrapper = mountEditor(mockEmit as unknown as KsEditorEmit)
        await flushPromises()

        getModelMarkers.mockReturnValue([])
        markerTrigger([{toString: () => "inmemory://test.yaml"}])

        expect(mockEmit).toHaveBeenCalledWith("editor-warnings", [])

        wrapper.unmount()
    })

    it("ignores marker changes if the URI does not match the active model", async () => {
        const wrapper = mountEditor(mockEmit as unknown as KsEditorEmit)
        await flushPromises()

        markerTrigger([{toString: () => "inmemory://other-file.yaml"}])

        expect(getModelMarkers).not.toHaveBeenCalled()
        expect(mockEmit).not.toHaveBeenCalledWith("editor-warnings", expect.anything())

        wrapper.unmount()
    })

    it("disposes the onDidChangeMarkers listener when the editor unmounts", async () => {
        const wrapper = mountEditor(mockEmit as unknown as KsEditorEmit)
        await flushPromises()

        expect(onDidChangeMarkers).toHaveBeenCalledTimes(1)
        expect(markerDispose).not.toHaveBeenCalled()

        wrapper.unmount()

        expect(markerDispose).toHaveBeenCalledTimes(1)
    })
})