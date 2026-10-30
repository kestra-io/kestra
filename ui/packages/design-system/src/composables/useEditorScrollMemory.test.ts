import {describe, test, expect, vi, beforeEach} from "vitest"
import {ref} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"
import {useEditorScrollMemory} from "./useEditorScrollMemory"

const viewState = {cursorState: [], viewState: {scrollTop: 0}, contributionsState: {}}

function createEditor({scrollTop = 0, state = viewState}: {scrollTop?: number, state?: unknown} = {}) {
    let scrollListener: (() => void) | undefined
    const editor = {
        restoreViewState: vi.fn(),
        saveViewState: vi.fn(() => state),
        getScrollTop: vi.fn(() => scrollTop),
        setScrollTop: vi.fn(),
        getPosition: vi.fn(() => ({lineNumber: 1, column: 1})),
        onDidScrollChange: vi.fn((listener: () => void) => {
            scrollListener = listener
        }),
    }
    return {
        editor,
        asMonaco: editor as unknown as monaco.editor.IStandaloneCodeEditor,
        scroll: () => scrollListener?.(),
    }
}

describe("useEditorScrollMemory", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    test("save then load round-trips a value under editorScroll:<key>:<name>", () => {
        const {save, load} = useEditorScrollMemory(ref("flow.yaml"))

        save({line: 12}, "viewState")

        expect(localStorage.getItem("editorScroll:flow.yaml:viewState")).toBe("{\"line\":12}")
        expect(load("viewState")).toEqual({line: 12})
    })

    test("load returns the fallback when nothing is stored", () => {
        const {load} = useEditorScrollMemory(ref("flow.yaml"))

        expect(load("scrollTop", 42)).toBe(42)
    })

    test("load returns the fallback instead of throwing when the stored value is not valid JSON", () => {
        localStorage.setItem("editorScroll:flow.yaml:scrollTop", "{not json")
        const {load} = useEditorScrollMemory(ref("flow.yaml"))

        expect(load("scrollTop", 7)).toBe(7)
    })

    test("with an undefined scroll key, save writes nothing and load returns the fallback", () => {
        const {save, load} = useEditorScrollMemory(ref(undefined))

        save(100, "scrollTop")

        expect(localStorage).toHaveLength(0)
        expect(load("scrollTop", 3)).toBe(3)
    })

    test("restoreAndTrack restores the saved view state and scroll offset onto the editor", () => {
        localStorage.setItem("editorScroll:flow.yaml:viewState", JSON.stringify(viewState))
        localStorage.setItem("editorScroll:flow.yaml:scrollTop", "250")
        const {editor, asMonaco} = createEditor()

        useEditorScrollMemory(ref("flow.yaml")).restoreAndTrack(asMonaco)

        expect(editor.restoreViewState).toHaveBeenCalledWith(viewState)
        expect(editor.setScrollTop).toHaveBeenCalledWith(250)
    })

    test("restoreAndTrack persists the new position when the editor scrolls", () => {
        const scrolled = {...viewState, viewState: {scrollTop: 480}}
        const {editor, asMonaco, scroll} = createEditor({scrollTop: 480, state: scrolled})

        useEditorScrollMemory(ref("flow.yaml")).restoreAndTrack(asMonaco)
        scroll()

        expect(editor.onDidScrollChange).toHaveBeenCalledOnce()
        expect(localStorage.getItem("editorScroll:flow.yaml:scrollTop")).toBe("480")
        expect(JSON.parse(localStorage.getItem("editorScroll:flow.yaml:viewState") ?? "null")).toEqual(scrolled)
    })

    test("saveViewState writes the editor's view state", () => {
        const {editor, asMonaco} = createEditor()

        useEditorScrollMemory(ref("flow.yaml")).saveViewState(asMonaco)

        expect(editor.saveViewState).toHaveBeenCalledOnce()
        expect(JSON.parse(localStorage.getItem("editorScroll:flow.yaml:viewState") ?? "null")).toEqual(viewState)
    })

    test("saveViewState does nothing without a scroll key", () => {
        const {editor, asMonaco} = createEditor()

        useEditorScrollMemory(ref(undefined)).saveViewState(asMonaco)

        expect(editor.saveViewState).not.toHaveBeenCalled()
        expect(localStorage).toHaveLength(0)
    })
})
