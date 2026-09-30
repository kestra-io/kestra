import type {languages} from "monaco-editor/editor/editor.api"

const PLUGIN_TYPE = /^((?:[a-z_$][\w$]*\.)+)([A-Z][\w$]*)$/

/** Splits a plugin type into class name and package; `suggestionLabel` in the design system rejoins the two. */
export function splitPluginTypeLabel(label: string): languages.CompletionItemLabel | undefined {
    const match = PLUGIN_TYPE.exec(label)
    if (!match) {
        return undefined
    }

    return {label: match[2], description: match[1].slice(0, -1)}
}
