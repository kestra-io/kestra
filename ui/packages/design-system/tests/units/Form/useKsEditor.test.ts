import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import * as monaco from "monaco-editor/editor/editor.api"
import {useKsEditor} from "../../../src/composables/useKsEditor"
import {createPebbleEntryTracker} from "../../../src/utils/pebbleBlock"
import type {KsEditorEmit, KsEditorTemplateRefs, ResolvedKsEditorProps} from "../../../src/utils/editorTypes"

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

vi.mock("../../../src/utils/monacoSetup", () => ({
    configureMonacoTypescript: vi.fn(),
    editorModelUid: vi.fn(() => "uid"),
    getOrCreateOverflowWidgetsDomNode: vi.fn(() => document.createElement("div")),
    registerMonacoThemes: vi.fn(),
}))

vi.mock("../../../src/composables/taskIcon", () => ({
    useTaskIcon: vi.fn(() => ({render: () => null})),
    TASK_ICON_INJECTION_KEY: Symbol(),
}))

vi.mock("../../../src/composables/useEditorDecorations", () => ({
    useEditorDecorations: vi.fn(() => ({
        attach: vi.fn(),
        highlightPebble: vi.fn(),
        highlightLinesRange: vi.fn(),
        clearLinesRangeHighlights: vi.fn(),
        highlightInitial: vi.fn(),
    })),
}))

vi.mock("../../../src/composables/useEditorContentWidget", () => ({
    useEditorContentWidget: vi.fn(() => ({
        showWidgetContent: ref(false),
        addContentWidget: vi.fn(),
        removeContentWidget: vi.fn(),
    })),
}))

vi.mock("../../../src/composables/useEditorScrollMemory", () => ({
    useEditorScrollMemory: vi.fn(() => ({
        restoreAndTrack: vi.fn(),
        saveViewState: vi.fn(),
    })),
}))

vi.mock("../../../src/composables/useEditorDatePicker", () => ({
    useEditorDatePicker: vi.fn(() => ({
        selectedDate: ref(null),
        shown: ref(false),
        startOfToday: ref(null),
        insertSelectedDate: vi.fn(),
    })),
}))

vi.mock("../../../src/composables/useSuggestWidgetIcons", () => ({
    useSuggestWidgetIcons: vi.fn(() => ({
        observeAndResize: vi.fn(),
        teardown: vi.fn(),
    })),
}))

vi.mock("../../../src/composables/useEditorActions", () => ({
    registerEditorActions: vi.fn(() => ({dispose: vi.fn()})),
}))

vi.mock("../../../src/utils/pebbleBlock", () => ({
    createPebbleEntryTracker: vi.fn(() => ({track: vi.fn(), consumeEntered: vi.fn(() => false)})),
    cursorPebbleBlockKey: vi.fn(() => ""),
    isCursorInPebbleBlock: vi.fn(() => false),
    isPebbleEnabled: vi.fn(() => false),
}))

vi.mock("../../../src/composables/PlaceholderContentWidget", () => ({
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

beforeEach(() => {
    vi.clearAllMocks()
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
    vi.useRealTimers()
    vi.unstubAllGlobals()
})

describe("useKsEditor suggestions on cursor settle", () => {
    async function mountAndSettle({selectionEmpty, suggestOpen, enteredPebble}: {selectionEmpty: boolean, suggestOpen: boolean, enteredPebble: boolean}) {
        vi.mocked(createPebbleEntryTracker).mockReturnValueOnce({track: vi.fn(), consumeEntered: () => enteredPebble})
        const wrapper = mountEditor(vi.fn() as unknown as KsEditorEmit)
        await flushPromises()

        const editor = vi.mocked(monaco.editor.create).mock.results[0].value
        const suggestController = editor.getContribution.mock.results[0].value
        suggestController.model.state = suggestOpen ? 1 : 0
        editor.getSelection.mockReturnValue({isEmpty: () => selectionEmpty})

        vi.useFakeTimers()
        editor.onDidChangeCursorPosition.mock.calls[0][0]()
        vi.advanceTimersByTime(300)
        wrapper.unmount()

        return {trigger: editor.trigger, cancelSuggestWidget: suggestController.cancelSuggestWidget}
    }

    it("closes the open suggest widget without reopening it when text is selected", async () => {
        const {trigger, cancelSuggestWidget} = await mountAndSettle({selectionEmpty: false, suggestOpen: true, enteredPebble: false})

        expect(cancelSuggestWidget).toHaveBeenCalledTimes(1)
        expect(trigger).not.toHaveBeenCalled()
    })

    it("does not open the suggest widget when a selection crosses a Pebble block", async () => {
        const {trigger} = await mountAndSettle({selectionEmpty: false, suggestOpen: false, enteredPebble: true})

        expect(trigger).not.toHaveBeenCalled()
    })

    it("opens the suggest widget when the cursor alone enters a Pebble block", async () => {
        const {trigger} = await mountAndSettle({selectionEmpty: true, suggestOpen: false, enteredPebble: true})

        expect(trigger).toHaveBeenCalledWith("triggerSuggestionsInPebbleBlock", "editor.action.triggerSuggest", {})
    })
})
