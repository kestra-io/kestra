import type {SyntaxRole} from "./types.ts"

/** The roles a Pebble block is painted with. Delimiters, variables and anything unknown keep the `pebble` color. */
export type PebbleRole = Extract<SyntaxRole, "pebble" | "keyword" | "constant" | "string" | "number" | "function" | "operator" | "punctuation">

export const PEBBLE_ROLES: readonly PebbleRole[] = ["pebble", "keyword", "constant", "string", "number", "function", "operator", "punctuation"]

/** A run of a Pebble block painted with one role, as `[start, end)` offsets into the text. */
export type PebbleToken = [start: number, end: number, role: PebbleRole]

const KEYWORDS = ["if", "elseif", "else", "endif", "for", "endfor", "in", "set", "macro", "endmacro", "block", "endblock", "filter", "endfilter", "verbatim", "endverbatim", "is", "not", "and", "or", "contains", "equals", "isIn"]
const CONSTANTS = ["true", "false", "null", "none"]

const WORD = "(?<![\\w.])(?:%s)(?![\\w.])"
const STRING = "'(?:(?!\\}\\}|%\\})[^'\\\\\\n]|\\\\.)*'|\"(?:(?!\\}\\}|%\\})[^\"\\\\\\n]|\\\\.)*\""
const NUMBER = "\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"
const IDENTIFIER = "[A-Za-z_]\\w*"
const OPERATOR = "\\?\\?\\??|==|!=|<=|>=|\\.\\.|[-+*/%~<>=!?|]"
const PUNCTUATION = "[()\\[\\]{},:]"

/**
 * TextMate injection for Pebble in YAML, shipped in the VS Code extension, with the rules of `pebbleTokens` except
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
        expression: {name: "meta.embedded.expression.pebble", begin: "\\{\\{-?", end: "-?\\}\\}|(?=^\\S)", patterns: [{include: "#inside"}]},
        statement: {name: "meta.embedded.statement.pebble", begin: "\\{%-?(?=[ \\t]*[A-Za-z_]\\w)", end: "-?%\\}|(?=^\\S)", patterns: [{include: "#inside"}]},
        inside: {
            patterns: [
                {match: STRING, name: "string.quoted.pebble"},
                {match: WORD.replace("%s", KEYWORDS.join("|")), name: "keyword.control.pebble"},
                {match: WORD.replace("%s", CONSTANTS.join("|")), name: "constant.language.pebble"},
                {match: `(\\|)[ \\t]*(${IDENTIFIER})?`, captures: {1: {name: "keyword.operator.pebble"}, 2: {name: "support.function.pebble"}}},
                {match: `${IDENTIFIER}(?=[ \\t]*\\()`, name: "support.function.pebble"},
                {match: IDENTIFIER, name: "variable.other.pebble"},
                {match: NUMBER, name: "constant.numeric.pebble"},
                {match: OPERATOR, name: "keyword.operator.pebble"},
                {match: PUNCTUATION, name: "punctuation.pebble"},
            ],
        },
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

const KEYWORD_SET = new Set(KEYWORDS)
const CONSTANT_SET = new Set(CONSTANTS)
const STRING_RE = new RegExp(STRING, "y")
const NUMBER_RE = new RegExp(NUMBER, "y")
const IDENTIFIER_RE = new RegExp(IDENTIFIER, "y")
const OPERATOR_RE = new RegExp(OPERATOR, "y")
const PUNCTUATION_RE = new RegExp(PUNCTUATION, "y")
const SPACE_RE = /[ \t\r\n]+/y
const NEWLINE_RE = /[ \t\r]*\n/y
const CALL_RE = /[ \t]*\(/y

function matchAt(re: RegExp, text: string, at: number): number {
    re.lastIndex = at
    return re.test(text) ? re.lastIndex : -1
}

/**
 * The tokens of every Pebble block of `text`, in order. Each block covers exactly the range `pebbleBlocks` gives
 * it, cut into runs painted with one role: keywords, constants, strings, numbers, functions (a name before `(` or
 * after a `|` filter pipe), operators and punctuation, with delimiters, variables and whitespace staying `pebble`.
 * Adjacent runs of one role are merged, so `{{ inputs.name }}` is a single token.
 */
export function pebbleTokens(text: string): PebbleToken[] {
    const tokens: PebbleToken[] = []
    const push = (start: number, end: number, role: PebbleRole) => {
        const last = tokens[tokens.length - 1]
        if (last && last[2] === role && last[1] === start) {
            last[1] = end
        } else {
            tokens.push([start, end, role])
        }
    }
    for (const [start, end] of pebbleBlocks(text)) {
        const closer = text[start + 1] === "{" ? "}" : "%"
        const closed = end - start >= 4 && text[end - 1] === "}" && text[end - 2] === closer
        const contentEnd = closed ? end - (text[end - 3] === "-" ? 3 : 2) : end
        let at = text[start + 2] === "-" ? start + 3 : start + 2
        tokens.push([start, at, "pebble"])
        let afterPipe = false
        let afterDot = false
        while (at < contentEnd) {
            const char = text.charCodeAt(at)
            let next = -1
            let role: PebbleRole = "pebble"
            if (char === 32 || char === 9 || char === 10 || char === 13) {
                next = Math.min(matchAt(SPACE_RE, text, at), contentEnd)
                if (afterPipe && matchAt(NEWLINE_RE, text, at) !== -1) afterPipe = false
                afterDot = false
                push(at, next, "pebble")
                at = next
                continue
            }
            if (char === 39 || char === 34) {
                next = matchAt(STRING_RE, text, at)
                role = "string"
            } else if (char === 95 || (char | 32) >= 97 && (char | 32) <= 122) {
                next = matchAt(IDENTIFIER_RE, text, at)
                const word = text.slice(at, next)
                const standalone: boolean = !afterDot && text.charCodeAt(next) !== 46
                role = afterPipe ? "function"
                    : standalone && KEYWORD_SET.has(word) ? "keyword"
                    : standalone && CONSTANT_SET.has(word) ? "constant"
                    : matchAt(CALL_RE, text, next) !== -1 ? "function"
                    : "pebble"
            } else if (char >= 48 && char <= 57) {
                next = matchAt(NUMBER_RE, text, at)
                role = "number"
            } else if ((next = matchAt(OPERATOR_RE, text, at)) !== -1) {
                role = "operator"
            } else if ((next = matchAt(PUNCTUATION_RE, text, at)) !== -1) {
                role = "punctuation"
            }
            if (next === -1 || next > contentEnd) {
                next = at + 1
                role = "pebble"
            }
            afterPipe = role === "operator" && char === 124
            afterDot = role === "pebble" && char === 46
            push(at, next, role)
            at = next
        }
        if (closed) push(contentEnd, end, "pebble")
    }
    return tokens
}
