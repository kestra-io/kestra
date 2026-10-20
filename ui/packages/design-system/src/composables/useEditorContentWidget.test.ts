import {ref} from "vue"
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import * as monaco from "monaco-editor/editor/editor.api"
import {useEditorContentWidget} from "./useEditorContentWidget"

describe("useEditorContentWidget", () => {
    let editor: {
        addContentWidget: ReturnType<typeof vi.fn>
        removeContentWidget: ReturnType<typeof vi.fn>
    }
    let editorRoot: HTMLDivElement

    beforeEach(() => {
        vi.useFakeTimers()

        editor = {
            addContentWidget: vi.fn(),
            removeContentWidget: vi.fn(),
        }

        editorRoot = document.createElement("div")
        const scrollableElement = document.createElement("div")
        scrollableElement.className = "monaco-scrollable-element"
        editorRoot.appendChild(scrollableElement)

        vi.spyOn(scrollableElement, "getBoundingClientRect").mockReturnValue({
            width: 500,
            height: 300,
            top: 0,
            left: 0,
            right: 500,
            bottom: 300,
            x: 0,
            y: 0,
            toJSON: () => ({}),
        })
    })

    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
        document.body.innerHTML = ""
    })

    it("adds a content widget with a stable ID", async () => {
        const {addContentWidget} = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(editorRoot),
        })

        const widget = {
            id: "test-widget",
            position: {lineNumber: 1, column: 1},
            height: 4,
            right: "10px",
        }

        const promise = addContentWidget(widget)

        expect(editor.addContentWidget).toHaveBeenCalledTimes(1)

        const registeredWidget = editor.addContentWidget.mock.calls[0][0]
        expect(registeredWidget.getId()).toBe("test-widget")
        expect(registeredWidget.getId()).toBe("test-widget")

        document.body.appendChild(registeredWidget.getDomNode())
        await vi.runAllTimersAsync()
        await promise
    })

    it("removes the content widget when requested", async () => {
        const {addContentWidget, removeContentWidget} = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(editorRoot),
        })

        const widget = {
            id: "widget-to-remove",
            position: {lineNumber: 2, column: 1},
            height: 3,
            right: "10px",
        }

        const promise = addContentWidget(widget)
        document.body.appendChild(editor.addContentWidget.mock.calls[0][0].getDomNode())
        await vi.runAllTimersAsync()
        await promise

        removeContentWidget(widget.id)

        expect(editor.removeContentWidget).toHaveBeenCalledTimes(1)
        expect(editor.removeContentWidget.mock.calls[0][0].getId()).toBe(widget.id)
    })

    it("does not throw when removal is called twice", () => {
        const {removeContentWidget} = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(editorRoot),
        })

        expect(() => {
            removeContentWidget("widget-id")
            removeContentWidget("widget-id")
        }).not.toThrow()

        expect(editor.removeContentWidget).toHaveBeenCalledTimes(2)
    })

    it("creates the widget DOM node only once", async () => {
        const {addContentWidget} = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(editorRoot),
        })

        const widget = {
            id: "stable-node",
            position: {lineNumber: 1, column: 1},
            height: 2,
            right: "10px",
        }

        const firstPromise = addContentWidget(widget)
        const registeredWidget = editor.addContentWidget.mock.calls[0][0]
        const firstNode = registeredWidget.getDomNode()

        const secondPromise = addContentWidget(widget)
        const secondNode = editor.addContentWidget.mock.calls[1][0].getDomNode()

        expect(firstNode).toBe(secondNode)

        document.body.appendChild(firstNode)
        await vi.runAllTimersAsync()
        await Promise.all([firstPromise, secondPromise])
    })

    it("does nothing when no editor is available", async () => {
        const {addContentWidget, removeContentWidget, showWidgetContent} =
            useEditorContentWidget({
                codeEditor: () => undefined,
                editorRoot: ref(editorRoot),
            })

        await addContentWidget({
            id: "no-editor",
            position: {lineNumber: 1, column: 1},
            height: 2,
            right: "10px",
        })

        removeContentWidget("no-editor")

        expect(editor.addContentWidget).not.toHaveBeenCalled()
        expect(editor.removeContentWidget).not.toHaveBeenCalled()
        expect(showWidgetContent.value).toBe(false)
    })
})