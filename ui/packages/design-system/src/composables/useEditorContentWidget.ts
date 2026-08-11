import {ref, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"

const CONTENT_NODE_POLL_MS = 30

export interface EditorContentWidgetContext {
    codeEditor: () => monaco.editor.IStandaloneCodeEditor | undefined
    editorRoot: Ref<HTMLElement | null | undefined>
}

export function useEditorContentWidget(ctx: EditorContentWidgetContext) {
    const showWidgetContent = ref(false)

    const widgetNode = (() => {
        const node = document.createElement("div")
        node.className = "editor-content-widget"
        const content = document.createElement("div")
        content.className = "editor-content-widget-content"
        node.appendChild(content)
        return node
    })()

    async function waitForWidgetContentNode(): Promise<void> {
        await new Promise(resolve => setTimeout(resolve, CONTENT_NODE_POLL_MS))
        if (document.querySelector(".editor-content-widget-content") === null) {
            return waitForWidgetContentNode()
        }
    }

    async function addContentWidget(widget: {id: string, position: monaco.IPosition, height: number, right: string}) {
        const editor = ctx.codeEditor()
        if (!editor) return

        editor.addContentWidget({
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
                const rect = ctx.editorRoot.value!.querySelector(".monaco-scrollable-element")!.getBoundingClientRect()
                widgetNode.style.left = `calc(${rect.width}px - 150px - ${widget.right})`
            },
        })

        await waitForWidgetContentNode()
        showWidgetContent.value = true
    }

    function removeContentWidget(id: string) {
        showWidgetContent.value = false
        const editor = ctx.codeEditor()
        if (!editor) return
        editor.removeContentWidget({
            getId: () => id,
            getPosition() { return {position: {lineNumber: 0, column: 0}, preference: []} },
            getDomNode: () => widgetNode,
        })
    }

    return {showWidgetContent, addContentWidget, removeContentWidget}
}
