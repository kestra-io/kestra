import * as monaco from "monaco-editor/editor/editor.api"

export interface EditorGutterAction {
    id: string
    line: number
    title?: string
}

export interface EditorGutterActionEvent {
    id: string
    shiftKey: boolean
}

export const GUTTER_ACTION_CLASS = "ks-editor-gutter-action"

export function useEditorGutterActions(onAction: (event: EditorGutterActionEvent) => void) {
    let editor: monaco.editor.IStandaloneCodeEditor | undefined
    let collection: monaco.editor.IEditorDecorationsCollection | undefined
    let actions: EditorGutterAction[] = []

    function paint() {
        if (!editor || !collection) return
        editor.updateOptions({glyphMargin: actions.length > 0})
        collection.set(actions.map(action => ({
            range: new monaco.Range(action.line, 1, action.line, 1),
            options: {
                glyphMarginClassName: GUTTER_ACTION_CLASS,
                glyphMarginHoverMessage: action.title ? {value: action.title} : undefined,
            },
        })))
    }

    function attach(mounted: monaco.editor.IStandaloneCodeEditor) {
        editor = mounted
        collection = mounted.createDecorationsCollection()
        mounted.onMouseDown((event) => {
            if (event.target.type !== monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN) return
            const line = event.target.position?.lineNumber
            const action = actions.find(candidate => candidate.line === line)
            if (!action) return
            event.event.preventDefault()
            onAction({id: action.id, shiftKey: event.event.shiftKey})
        })
        paint()
    }

    function setGutterActions(next: EditorGutterAction[]) {
        actions = next
        paint()
    }

    return {attach, setGutterActions}
}
