import {ref} from "vue"
import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import type * as monaco from "monaco-editor/editor/editor.api"
import {useEditorScrollMemory} from "./useEditorScrollMemory"

const viewState = {cursorState: [], viewState: {scrollTop: 140}, contributionsState: {}}

function createEditor() {
    const spies = {
        restoreViewState: vi.fn(),
        saveViewState: vi.fn(() => viewState),
        getScrollTop: vi.fn(() => 140),
        setScrollTop: vi.fn(),
        getPosition: vi.fn(() => ({lineNumber: 12, column: 1})),
        revealLineInCenterIfOutsideViewport: vi.fn(),
        onDidScrollChange: vi.fn<(listener: () => void) => void>(),
    }

    return {editor: spies as unknown as monaco.editor.IStandaloneCodeEditor, spies}
}

describe("useEditorScrollMemory", () => {
    beforeEach(() => localStorage.clear())

    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
        localStorage.clear()
    })

    test("round-trips a value under editorScroll:<key>:<name>", () => {
        const {save, load} = useEditorScrollMemory(ref("flow.yaml"))

        save(viewState, "viewState")

        expect(localStorage.getItem("editorScroll:flow.yaml:viewState")).toBe(JSON.stringify(viewState))
        expect(load("viewState")).toEqual(viewState)
    })

    test("returns the supplied fallback when nothing is stored", () => {
        const {load} = useEditorScrollMemory(ref("flow.yaml"))

        expect(load("scrollTop", 42)).toBe(42)
    })

    test("returns the fallback when the stored JSON is invalid", () => {
        localStorage.setItem("editorScroll:flow.yaml:scrollTop", "{invalid")
        const {load} = useEditorScrollMemory(ref("flow.yaml"))

        expect(load("scrollTop", 42)).toBe(42)
    })

    test("returns the fallback when storage cannot be read", () => {
        const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new DOMException("Storage is unavailable.", "SecurityError")
        })
        const {load} = useEditorScrollMemory(ref("flow.yaml"))

        expect(load("scrollTop", 42)).toBe(42)
        expect(getItem).toHaveBeenCalledWith("editorScroll:flow.yaml:scrollTop")
    })

    test("does not throw when storage cannot be written", () => {
        const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
            throw new DOMException("Storage is full.", "QuotaExceededError")
        })
        const {save} = useEditorScrollMemory(ref("flow.yaml"))

        expect(() => save(140, "scrollTop")).not.toThrow()
        expect(setItem).toHaveBeenCalledWith("editorScroll:flow.yaml:scrollTop", "140")
    })

    test("skips storage and returns the fallback without a scroll key", () => {
        const getItem = vi.spyOn(Storage.prototype, "getItem")
        const setItem = vi.spyOn(Storage.prototype, "setItem")
        const {save, load} = useEditorScrollMemory(ref(undefined))

        save(140, "scrollTop")

        expect(load("scrollTop", 42)).toBe(42)
        expect(getItem).not.toHaveBeenCalled()
        expect(setItem).not.toHaveBeenCalled()
    })

    test("restores the saved view state and scroll offset", () => {
        localStorage.setItem("editorScroll:flow.yaml:viewState", JSON.stringify(viewState))
        localStorage.setItem("editorScroll:flow.yaml:scrollTop", "250")
        const {editor, spies} = createEditor()

        useEditorScrollMemory(ref("flow.yaml")).restoreAndTrack(editor)

        expect(spies.restoreViewState).toHaveBeenCalledWith(viewState)
        expect(spies.revealLineInCenterIfOutsideViewport).toHaveBeenCalledWith(12)
        expect(spies.setScrollTop).toHaveBeenCalledWith(250)
    })

    test("starts at the top without restoring a view state when none is saved", () => {
        const {editor, spies} = createEditor()

        useEditorScrollMemory(ref("flow.yaml")).restoreAndTrack(editor)

        expect(spies.restoreViewState).not.toHaveBeenCalled()
        expect(spies.revealLineInCenterIfOutsideViewport).not.toHaveBeenCalled()
        expect(spies.setScrollTop).toHaveBeenCalledWith(0)
    })

    test("persists the current view state and scroll offset through the scroll listener", async () => {
        vi.useFakeTimers()
        const {editor, spies} = createEditor()
        useEditorScrollMemory(ref("flow.yaml")).restoreAndTrack(editor)

        expect(spies.onDidScrollChange).toHaveBeenCalledOnce()
        const onScroll = spies.onDidScrollChange.mock.calls[0][0]
        onScroll()

        expect(localStorage.getItem("editorScroll:flow.yaml:viewState")).toBe(JSON.stringify(viewState))
        expect(localStorage.getItem("editorScroll:flow.yaml:scrollTop")).toBe("140")

        const scrolledState = {...viewState, viewState: {scrollTop: 480}}
        spies.saveViewState.mockReturnValue(scrolledState)
        spies.getScrollTop.mockReturnValue(480)
        onScroll()
        await vi.advanceTimersByTimeAsync(100)

        expect(localStorage.getItem("editorScroll:flow.yaml:viewState")).toBe(JSON.stringify(scrolledState))
        expect(localStorage.getItem("editorScroll:flow.yaml:scrollTop")).toBe("480")
    })

    test("does not restore or register a scroll listener without a scroll key", () => {
        const {editor, spies} = createEditor()

        useEditorScrollMemory(ref(undefined)).restoreAndTrack(editor)

        expect(spies.restoreViewState).not.toHaveBeenCalled()
        expect(spies.setScrollTop).not.toHaveBeenCalled()
        expect(spies.onDidScrollChange).not.toHaveBeenCalled()
    })

    test("saves the editor's view state", () => {
        const {editor, spies} = createEditor()

        useEditorScrollMemory(ref("flow.yaml")).saveViewState(editor)

        expect(spies.saveViewState).toHaveBeenCalledOnce()
        expect(localStorage.getItem("editorScroll:flow.yaml:viewState")).toBe(JSON.stringify(viewState))
    })

    test("does not read or save the editor's view state without a scroll key", () => {
        const {editor, spies} = createEditor()

        useEditorScrollMemory(ref(undefined)).saveViewState(editor)

        expect(spies.saveViewState).not.toHaveBeenCalled()
        expect(localStorage).toHaveLength(0)
    })
})
