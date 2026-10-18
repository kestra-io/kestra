import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"
import {useEditorContentWidget} from "./useEditorContentWidget"

type ContentWidget = Parameters<monaco.editor.IStandaloneCodeEditor["addContentWidget"]>[0]

function createEditor() {
    const widgets: ContentWidget[] = []
    const removed: ContentWidget[] = []
    const editor = {
        addContentWidget: vi.fn((widget: ContentWidget) => {
            widgets.push(widget)
            document.body.appendChild(widget.getDomNode())
        }),
        removeContentWidget: vi.fn((widget: ContentWidget) => {
            removed.push(widget)
            widget.getDomNode().remove()
        }),
    }
    return {editor, widgets, removed}
}

describe("useEditorContentWidget", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        document.body.innerHTML = ""
    })

    afterEach(() => {
        vi.useRealTimers()
        document.body.innerHTML = ""
    })

    test("adds the content widget with a stable id", async () => {
        const {editor, widgets} = createEditor()
        const composable = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(document.createElement("div")),
        })

        const pending = composable.addContentWidget({
            id: "test-widget",
            position: {lineNumber: 1, column: 1},
            height: 4,
            right: "1rem",
        })
        await vi.advanceTimersByTimeAsync(30)
        await pending

        expect(editor.addContentWidget).toHaveBeenCalledOnce()
        expect(widgets[0].getId()).toBe("test-widget")
        expect(composable.showWidgetContent.value).toBe(true)
    })

    test("removes a widget when requested and tolerates repeated disposal", async () => {
        const {editor, widgets, removed} = createEditor()
        const composable = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(document.createElement("div")),
        })
        const widget = {
            id: "disposable-widget",
            position: {lineNumber: 1, column: 1},
            height: 3,
            right: "0",
        }

        const pending = composable.addContentWidget(widget)
        await vi.advanceTimersByTimeAsync(30)
        await pending

        composable.removeContentWidget(widget.id)
        expect(composable.showWidgetContent.value).toBe(false)
        expect(editor.removeContentWidget).toHaveBeenCalledOnce()
        expect(removed[0].getId()).toBe(widget.id)

        expect(() => composable.removeContentWidget(widget.id)).not.toThrow()
        expect(editor.removeContentWidget).toHaveBeenCalledTimes(2)
        expect(widgets[0].getDomNode()).toBe(removed[0].getDomNode())
    })

    test("reuses the same DOM node across widget registrations", async () => {
        const {editor, widgets} = createEditor()
        const composable = useEditorContentWidget({
            codeEditor: () => editor as unknown as monaco.editor.IStandaloneCodeEditor,
            editorRoot: ref(document.createElement("div")),
        })
        const widget = {
            id: "reused-widget",
            position: {lineNumber: 1, column: 1},
            height: 2,
            right: "0",
        }

        const first = composable.addContentWidget(widget)
        await vi.advanceTimersByTimeAsync(30)
        await first
        const firstNode = widgets[0].getDomNode()

        composable.removeContentWidget(widget.id)
        const second = composable.addContentWidget(widget)
        await vi.advanceTimersByTimeAsync(30)
        await second

        expect(widgets[1].getDomNode()).toBe(firstNode)
    })

    test("does nothing when there is no editor", async () => {
        const composable = useEditorContentWidget({
            codeEditor: () => undefined,
            editorRoot: ref(null),
        })

        await expect(composable.addContentWidget({
            id: "unused-widget",
            position: {lineNumber: 1, column: 1},
            height: 2,
            right: "0",
        })).resolves.toBeUndefined()

        expect(composable.showWidgetContent.value).toBe(false)
        expect(document.querySelector(".editor-content-widget")).toBeNull()
        expect(() => composable.removeContentWidget("unused-widget")).not.toThrow()
    })
})
