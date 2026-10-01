import {onBeforeUnmount, watch, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"

import {violationMarkers} from "../utils/violationMarkers"
import type {ValidationError} from "../utils/validationErrors"

const OWNER = "backend-validation"

export function useViolationMarkers(options: {
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    errors: Ref<ValidationError[] | undefined>
}) {
    // The model outlives its editor (it is cached per tab), so clear the one written to, not the current one.
    let marked: {model: monaco.editor.ITextModel, decorations: string[]} | undefined

    function clear() {
        if (marked && !marked.model.isDisposed()) {
            monaco.editor.setModelMarkers(marked.model, OWNER, [])
            marked.model.deltaDecorations(marked.decorations, [])
        }
        marked = undefined
    }

    function apply(editor: monaco.editor.IStandaloneCodeEditor | undefined, errors: ValidationError[] | undefined) {
        clear()
        const model = editor?.getModel()
        if (!model) return
        const markers = violationMarkers(model.getValue(), errors)
        monaco.editor.setModelMarkers(model, OWNER, markers.map(({message, lines}) => ({
            ...lines[0],
            message,
            severity: monaco.MarkerSeverity.Error,
        })))
        // One Problems entry per violation: the other lines of a block get the same squiggle without a marker.
        const decorations = model.deltaDecorations([], markers.flatMap(({message, lines}) => lines.slice(1).map(range => ({
            range,
            options: {className: "squiggly-error", hoverMessage: {value: message, supportHtml: false}},
        }))))
        marked = {model, decorations}
    }

    // Only a new response re-resolves: Monaco shifts existing markers along with the user's edits.
    watch([options.editor, options.errors], ([editor, errors]) => apply(editor, errors), {immediate: true})

    onBeforeUnmount(clear)
}
