import {computed, toValue, watch, type MaybeRefOrGetter} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"
import {findDuplicateTaskIds} from "../utils/yamlValidation"
import type {EditorOptions} from "../utils/editorTypes"

type CodeEditor = monaco.editor.IStandaloneCodeEditor

export interface FlowYamlEditorContext {
    isFlowYaml: MaybeRefOrGetter<boolean>
    duplicateTaskIdMarkers: MaybeRefOrGetter<boolean | undefined>
    value: MaybeRefOrGetter<string>
    codeEditor: MaybeRefOrGetter<CodeEditor | undefined>
}

function hasVisibleInlineGhostText(editor: CodeEditor): boolean {
    return editor.getDomNode()?.querySelector(".ghost-text") !== null
}

function isTypeLine(lineContent: string): boolean {
    return /^\s*(?:-\s*)?type\s*:\s*.+\s*$/.test(lineContent)
}

export function useFlowYamlEditor(ctx: FlowYamlEditorContext) {
    const constructionOptions = computed<EditorOptions>(() => toValue(ctx.isFlowYaml) ? {inlineSuggest: {enabled: true}} : {})

    const duplicateTaskIdsEnabled = computed(() => toValue(ctx.duplicateTaskIdMarkers) ?? toValue(ctx.isFlowYaml))

    function attach(editor: CodeEditor, isSuggestWidgetActive: () => boolean) {
        editor.onKeyDown((e) => {
            if (!toValue(ctx.isFlowYaml)) return

            if (isSuggestWidgetActive() && (e.keyCode === monaco.KeyCode.Enter || e.keyCode === monaco.KeyCode.Tab)) {
                const current = toValue(ctx.codeEditor)
                const currentLine = current?.getModel()?.getLineContent(current.getPosition()?.lineNumber ?? 0) ?? ""
                if (isTypeLine(currentLine)) {
                    setTimeout(() => {
                        const ed = toValue(ctx.codeEditor)
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
                    toValue(ctx.codeEditor)?.trigger("inlineSuggestCommit", "editor.action.inlineSuggest.commit", {})
                    return
                }
                if (e.keyCode === monaco.KeyCode.Enter) {
                    toValue(ctx.codeEditor)?.trigger("inlineSuggestHide", "editor.action.inlineSuggest.hide", {})
                    return
                }
            }

            if (e.keyCode === monaco.KeyCode.Enter) {
                setTimeout(() => toValue(ctx.codeEditor)?.trigger("inlineSuggestTrigger", "editor.action.inlineSuggest.trigger", {}), 0)
            }
        })
    }

    watch(() => toValue(ctx.value), (newValue) => {
        if (!duplicateTaskIdsEnabled.value) return
        const model = toValue(ctx.codeEditor)?.getModel()
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
