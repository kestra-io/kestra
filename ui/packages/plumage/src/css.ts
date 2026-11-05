import type {CodeTheme} from "./types.ts"

/** `--code-<role>` custom properties for every syntax role plus the background, for styling editor decorations. */
export function cssVariables({syntax, editor}: CodeTheme): Record<string, string> {
    const vars: Record<string, string> = {"--code-background": editor.background}
    for (const [role, color] of Object.entries(syntax)) {
        vars[`--code-${role}`] = color
    }
    return vars
}
