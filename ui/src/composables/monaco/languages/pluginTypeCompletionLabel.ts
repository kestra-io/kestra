/** A completion label split into the part Monaco always shows and the part it is free to clip. */
export type SplitPluginTypeLabel = {label: string; description: string};

// A plugin type is a fully qualified class name: lowercase package segments, then the class itself.
const PLUGIN_TYPE = /^((?:[a-z_$][\w$]*\.)+)([A-Z][\w$]*)$/;

/**
 * Splits `io.kestra.plugin.ee.apps.execution.blocks.CreateExecutionButton` into the class name and its package.
 *
 * Monaco clips a completion row from the right, so a fully qualified type hides exactly the part that tells two
 * candidates apart: `CreateExecutionButton` and `CreateExecutionForm` both render as
 * `io.kestra.plugin.ee.apps.execution.blocks.CreateExec…`. Leading with the class name and demoting the package to
 * the dimmed description keeps the distinguishing part on screen - Monaco never shrinks the label of a row whose
 * label is an object, and caps the description at 70% of the row width.
 *
 * Returns undefined for anything that is not a plugin type - property keys, enum values, durations - which the
 * caller then leaves untouched.
 *
 * `MonacoEditor.vue` puts the two halves back together to resolve the row's plugin icon, so a change to which half
 * carries what has to be made there too.
 */
export function splitPluginTypeLabel(label: string): SplitPluginTypeLabel | undefined {
    const match = PLUGIN_TYPE.exec(label);
    if (!match) {
        return undefined;
    }

    return {label: match[2], description: match[1].slice(0, -1)};
}
