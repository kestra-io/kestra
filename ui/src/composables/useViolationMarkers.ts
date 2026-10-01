import {onBeforeUnmount, watch, type Ref} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"

import {violationMarkers} from "../utils/violationMarkers"
import type {ValidationError} from "../utils/validationErrors"

const OWNER = "backend-validation"
// Monaco's own error squiggle, so continuation lines look like the marker on the first one.
export const MONACO_ERROR_SQUIGGLE_CLASS = "squiggly-error"

/** Shows backend details verbatim in a markdown hover, like the plain-text marker message. */
function plainText(message: string): monaco.IMarkdownString {
    return {value: message.replace(/[\\`*_{}[\]()#+\-.!|<>~]/g, "\\$&")}
}

export function useViolationMarkers(options: {
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    errors: Ref<ValidationError[] | undefined>
}) {
    // The model outlives its editor (it is cached per tab), so clear the one written to, not the current one.
    let markedModel: monaco.editor.ITextModel | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined

    function clear() {
        if (markedModel && !markedModel.isDisposed()) {
            monaco.editor.setModelMarkers(markedModel, OWNER, [])
        }
        markedModel = undefined
        decorations?.clear()
        decorations = undefined
    }

    function apply(editor: monaco.editor.IStandaloneCodeEditor | undefined, errors: ValidationError[] | undefined) {
        clear()
        const model = editor?.getModel()
        if (!editor || !model) return
        const markers = violationMarkers(model.getValue(), errors)
        monaco.editor.setModelMarkers(model, OWNER, markers.map(({message, lines}) => ({
            ...lines[0],
            message,
            severity: monaco.MarkerSeverity.Error,
        })))
        markedModel = model
        // One Problems entry per violation: the other lines of a block get the same squiggle without a marker.
        decorations = editor.createDecorationsCollection(markers.flatMap(({message, lines}) => lines.slice(1).map(range => ({
            range,
            options: {className: MONACO_ERROR_SQUIGGLE_CLASS, hoverMessage: plainText(message)},
        }))))
    }

    // Only a new response re-resolves: Monaco shifts existing markers along with the user's edits.
    watch([options.editor, options.errors], ([editor, errors]) => apply(editor, errors), {immediate: true})

    onBeforeUnmount(clear)
}
