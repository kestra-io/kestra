import {describe, expect, it, vi} from "vitest"
import * as monaco from "monaco-editor/editor/editor.api"
import PlaceholderContentWidget from "./PlaceholderContentWidget"

vi.mock("monaco-editor/editor/editor.api", () => ({
    editor: {
        ContentWidgetPositionPreference: {EXACT: 0},
    },
}))

function fakeEditor(initialValue = "") {
    let value = initialValue
    let onChange: (() => void) | undefined
    const editor = {
        onDidChangeModelContent: (listener: () => void) => {
            onChange = listener
            return {dispose: () => {}}
        },
        getValue: () => value,
        addContentWidget: vi.fn(),
        removeContentWidget: vi.fn(),
        applyFontInfo: vi.fn(),
    }
    const type = (next: string) => {
        value = next
        onChange?.()
    }
    return {...editor, editor: editor as unknown as monaco.editor.ICodeEditor, type}
}

describe("PlaceholderContentWidget", () => {
    it("is added on construction when the editor is empty, and not when it has content", () => {
        const empty = fakeEditor()
        const widget = new PlaceholderContentWidget("Type here", empty.editor)
        expect(empty.addContentWidget).toHaveBeenCalledExactlyOnceWith(widget)

        const filled = fakeEditor("id: flow")
        const filledWidget = new PlaceholderContentWidget("Type here", filled.editor)
        expect(filled.addContentWidget).not.toHaveBeenCalledWith(filledWidget)
    })

    it("is removed when content is typed and added back when the editor is emptied", () => {
        const fake = fakeEditor()
        const widget = new PlaceholderContentWidget("Type here", fake.editor)

        fake.type("i")
        expect(fake.removeContentWidget).toHaveBeenCalledExactlyOnceWith(widget)

        fake.type("")
        expect(fake.addContentWidget).toHaveBeenCalledTimes(2)
        expect(fake.addContentWidget).toHaveBeenLastCalledWith(widget)
    })

    it("has a stable id", () => {
        const widget = new PlaceholderContentWidget("Type here", fakeEditor().editor)
        expect(widget.getId()).toBe("editor.widget.placeholderHint")
    })

    it("builds the DOM node once and returns the same element afterwards", () => {
        const fake = fakeEditor()
        const widget = new PlaceholderContentWidget("Type here", fake.editor)

        expect(widget.getDomNode()).toBe(widget.getDomNode())
        expect(fake.applyFontInfo).toHaveBeenCalledOnce()
    })

    it("renders the placeholder text in italics with the editor font", () => {
        const fake = fakeEditor()
        const node = new PlaceholderContentWidget("Type here", fake.editor).getDomNode()

        expect(node.textContent).toBe("Type here")
        expect(node.style.fontStyle).toBe("italic")
        expect(fake.applyFontInfo).toHaveBeenCalledWith(node)
    })

    it("is pinned to the start of the first line", () => {
        const widget = new PlaceholderContentWidget("Type here", fakeEditor().editor)
        expect(widget.getPosition()).toEqual({
            position: {lineNumber: 1, column: 1},
            preference: [monaco.editor.ContentWidgetPositionPreference.EXACT],
        })
    })

    it("removes itself from the editor when disposed", () => {
        const fake = fakeEditor()
        const widget = new PlaceholderContentWidget("Type here", fake.editor)

        widget.dispose()
        expect(fake.removeContentWidget).toHaveBeenCalledExactlyOnceWith(widget)
    })
})
