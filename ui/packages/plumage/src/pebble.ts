/**
 * TextMate injection for Pebble in YAML, shipped in the VS Code extension, with the rules of `pebbleBlocks` except
 * that YAML comments are skipped. Shiki and Expressive Code use `pebbleTransformer` / `pluginPebble` instead: an
 * injection runs at every tokenizer step and added 48% to Expressive Code's YAML rendering.
 */
export const pebbleInjection = {
    name: "pebble-injection",
    scopeName: "text.pebble.injection",
    injectTo: ["source.yaml"],
    injectionSelector: "L:source.yaml -comment -meta.embedded.expression.pebble -meta.embedded.statement.pebble",
    patterns: [
        {include: "#expression"},
        {include: "#statement"},
    ],
    repository: {
        expression: {name: "meta.embedded.expression.pebble", begin: "\\{\\{", end: "\\}\\}|(?=^\\S)"},
        statement: {name: "meta.embedded.statement.pebble", begin: "\\{%(?=-?[ \\t]*[A-Za-z_]\\w)", end: "%\\}|(?=^\\S)"},
    },
}

const STATEMENT_TAG = /^-?[ \t]*[A-Za-z_]\w/
const TOP_LEVEL_LINE = /\n(?=\S)/g

/**
 * The Pebble blocks of `text` as `[start, end)` offsets. A block is `{{ … }}`, or `{% … %}` opening on a tag name
 * of two or more characters, so a shell `printf '{%s'` is left alone. It runs to its first closer across lines, but
 * not past a line starting at column 0, so an unclosed block cannot swallow the rest of a YAML file. Linear time,
 * however many blocks are left unclosed.
 */
export function pebbleBlocks(text: string): [number, number][] {
    const blocks: [number, number][] = []
    const nextCloser = {"}}": -2, "%}": -2}
    let nextBoundary = -2
    let i = text.indexOf("{")
    while (i !== -1 && i < text.length - 1) {
        const second = text[i + 1]
        if (second !== "{" && !(second === "%" && STATEMENT_TAG.test(text.slice(i + 2, i + 66)))) {
            i = text.indexOf("{", i + 1)
            continue
        }
        const closer = second === "{" ? "}}" : "%}"
        if (nextCloser[closer] !== -1 && nextCloser[closer] < i + 2) {
            nextCloser[closer] = text.indexOf(closer, i + 2)
        }
        if (nextBoundary !== -1 && nextBoundary < i) {
            TOP_LEVEL_LINE.lastIndex = i
            nextBoundary = TOP_LEVEL_LINE.exec(text)?.index ?? -1
        }
        const close = nextCloser[closer]
        const end = close !== -1 && (nextBoundary === -1 || close < nextBoundary)
            ? close + 2
            : nextBoundary === -1 ? text.length : nextBoundary
        blocks.push([i, end])
        i = text.indexOf("{", end)
    }
    return blocks
}
