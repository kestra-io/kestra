import {beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick, reactive, ref, shallowRef} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"

const route = reactive({query: {} as Record<string, unknown>, fullPath: "/flows/edit/company/a/edit"})

vi.mock("vue-router", () => ({useRoute: () => route}))

import {parseRouteLine, useRevealRouteLine} from "./useRevealRouteLine"

const fakeEditor = (lineCount: number) => ({
    getModel: () => ({getLineCount: () => lineCount, getLineMaxColumn: () => 20}),
    focus: vi.fn(),
    setSelection: vi.fn(),
    revealLineInCenter: vi.fn(),
})

const setup = (editor: ReturnType<typeof fakeEditor> | undefined, enabled = true) => {
    const editorRef = shallowRef(editor as unknown as monaco.editor.IStandaloneCodeEditor | undefined)
    const source = ref<string | undefined>("a")
    effectScope().run(() => useRevealRouteLine(editorRef, source, ref(enabled)))
    return {editorRef, source}
}

describe("parseRouteLine", () => {
    it("accepts positive integers only", () => {
        expect(parseRouteLine("12")).toBe(12)
        expect(parseRouteLine(["3"])).toBe(3)
        expect(parseRouteLine("0")).toBeUndefined()
        expect(parseRouteLine("-2")).toBeUndefined()
        expect(parseRouteLine("1.5")).toBeUndefined()
        expect(parseRouteLine("abc")).toBeUndefined()
        expect(parseRouteLine(undefined)).toBeUndefined()
    })
})

describe("useRevealRouteLine", () => {
    beforeEach(() => {
        route.query = {line: "8"}
        route.fullPath = "/flows/edit/company/a/edit?line=8"
    })

    it("selects and centers the requested line once the editor is ready", async () => {
        const editor = fakeEditor(20)
        const {editorRef} = setup(undefined)
        editorRef.value = editor as unknown as monaco.editor.IStandaloneCodeEditor
        await nextTick()

        expect(editor.setSelection).toHaveBeenCalledWith({startLineNumber: 8, startColumn: 1, endLineNumber: 8, endColumn: 20})
        expect(editor.revealLineInCenter).toHaveBeenCalledWith(8)
    })

    it("waits for the source to reach the line", async () => {
        let lines = 3
        const editor = {...fakeEditor(0), getModel: () => ({getLineCount: () => lines, getLineMaxColumn: () => 20})}
        const {source} = setup(editor)
        await nextTick()
        expect(editor.revealLineInCenter).not.toHaveBeenCalled()

        lines = 20
        source.value = "loaded"
        await nextTick()
        expect(editor.revealLineInCenter).toHaveBeenCalledWith(8)
    })

    it("does not move the cursor again while the user edits", async () => {
        const editor = fakeEditor(20)
        const {source} = setup(editor)
        await nextTick()
        source.value = "edited"
        await nextTick()

        expect(editor.revealLineInCenter).toHaveBeenCalledTimes(1)
    })

    it("ignores the line outside the flow editor", async () => {
        const editor = fakeEditor(20)
        setup(editor, false)
        await nextTick()

        expect(editor.revealLineInCenter).not.toHaveBeenCalled()
    })
})
