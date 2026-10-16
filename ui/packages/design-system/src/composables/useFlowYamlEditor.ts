import {computed, watch, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"
import {findDuplicateTaskIds} from "../utils/yamlValidation"
import type {EditorOptions} from "../utils/editorTypes"

type CodeEditor = monaco.editor.IStandaloneCodeEditor

export interface FlowYamlEditorContext {
    isFlowYaml: Ref<boolean>
    duplicateTaskIdMarkers: Ref<boolean | undefined>
    value: () => string
    codeEditor: () => CodeEditor | undefined
}

function hasVisibleInlineGhostText(editor: CodeEditor): boolean {
    return editor.getDomNode()?.querySelector(".ghost-text") !== null
}

function isTypeLine(lineContent: string): boolean {
    return /^\s*(?:-\s*)?type\s*:\s*.+\s*$/.test(lineContent)
}

export function useFlowYamlEditor(ctx: FlowYamlEditorContext) {
    const constructionOptions = computed<EditorOptions>(() => ctx.isFlowYaml.value ? {inlineSuggest: {enabled: true}} : {})

    const duplicateTaskIdsEnabled = computed(() => ctx.duplicateTaskIdMarkers.value ?? ctx.isFlowYaml.value)

    function attach(editor: CodeEditor, isSuggestWidgetActive: () => boolean) {
        editor.onKeyDown((e) => {
            if (!ctx.isFlowYaml.value) return

            if (isSuggestWidgetActive() && (e.keyCode === monaco.KeyCode.Enter || e.keyCode === monaco.KeyCode.Tab)) {
                const current = ctx.codeEditor()
                const currentLine = current?.getModel()?.getLineContent(current.getPosition()?.lineNumber ?? 0) ?? ""
                if (isTypeLine(currentLine)) {
                    setTimeout(() => {
                        const ed = ctx.codeEditor()
                        if (!ed) return
                        const position = ed.getPosition()
                        if (!position) return
                        const acceptedLine = ed.getModel()?.getLineContent(position.lineNumber) ?? ""
                        if (!isTypeLine(acceptedLine)) return
                        ed.trigger("typeAcceptedInsertLine", "editor.action.insertLineAfter", {})
                        ed.trigger("typeAcceptedInlineSuggest", "editor.action.inlineSuggest.trigger", {})
                    }, 0)
                }
            }

            if (hasVisibleInlineGhostText(editor)) {
                if (e.keyCode === monaco.KeyCode.Tab) {
                    e.preventDefault(); e.stopPropagation()
                    ctx.codeEditor()?.trigger("inlineSuggestCommit", "editor.action.inlineSuggest.commit", {})
                    return
                }
                if (e.keyCode === monaco.KeyCode.Enter) {
                    ctx.codeEditor()?.trigger("inlineSuggestHide", "editor.action.inlineSuggest.hide", {})
                    return
                }
            }

            if (e.keyCode === monaco.KeyCode.Enter) {
                setTimeout(() => ctx.codeEditor()?.trigger("inlineSuggestTrigger", "editor.action.inlineSuggest.trigger", {}), 0)
            }
        })
    }

    watch(ctx.value, (newValue) => {
        if (!duplicateTaskIdsEnabled.value) return
        const model = ctx.codeEditor()?.getModel()
        if (!model) return

        monaco.editor.setModelMarkers(
            model,
            "duplicate-task-ids",
            findDuplicateTaskIds(newValue).map((m) => ({
                startLineNumber: m.startLineNumber,
                startColumn: m.startColumn,
                endLineNumber: m.endLineNumber,
                endColumn: m.endColumn,
                message: m.message,
                severity: monaco.MarkerSeverity.Error,
            })),
        )
    }, {immediate: true})

    return {constructionOptions, attach}
}
