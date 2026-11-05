/** A block scalar (`key: |`) holding code, as 0-based inclusive lines, with the indentation its lines share. */
export interface EmbeddedBlock {
    startLine: number
    endLine: number
    indent: number
    language: string
}

/** Returns the language a task property holds, such as its plugin schema's `$language`, or undefined when unknown. */
export type LanguageResolver = (taskType: string | undefined, key: string) => string | undefined

const EXTENSIONS: Record<string, string> = {
    py: "python", sql: "sql", sh: "shell", bash: "shell", js: "javascript", mjs: "javascript", ts: "typescript",
    json: "json", yaml: "yaml", yml: "yaml", r: "r", rb: "ruby", go: "go", java: "java", ps1: "powershell",
    md: "markdown", html: "html", xml: "xml", css: "css", tf: "hcl", hcl: "hcl", ini: "ini", lua: "lua", pl: "perl",
    php: "php", jl: "julia", cs: "csharp", graphql: "graphql", dockerfile: "dockerfile",
}

const SQL_KEYS = new Set(["sql", "afterSQL", "statement"])

/** Script task families and the language of their `script`, matching the plugins' `$language`. */
const SCRIPT_FAMILIES: [RegExp, string][] = [
    [/\.scripts\.(python|jython)\./, "python"],
    [/\.scripts\.shell\./, "shell"],
    [/\.scripts\.(node|bun)\./, "javascript"],
    [/\.scripts\.deno\./, "typescript"],
    [/\.scripts\.dotnet\./, "csharp"],
    [/\.scripts\.powershell\./, "powershell"],
    [/\.scripts\.(go|julia|lua|perl|php|r|ruby)\./, "$1"],
]

const HEADER = /^(\s*)(-\s+)?(?:"([^"]+)"|'([^']+)'|([^\s#'"][^:#]*?)):\s*[|>][-+1-9]{0,2}\s*(#.*)?$/
const ITEM_HEADER = /^(\s*)-\s+[|>][-+1-9]{0,2}\s*(#.*)?$/
const KEY = /^(\s*)(-\s+)?([^\s#'"][^:#]*?):(\s|$)/
const TYPE = /^\s*(-\s+)?type:\s*["']?([\w.$-]+)/

const indentOf = (line: string) => line.length - line.trimStart().length

function defaultLanguage(taskType: string | undefined, key: string): string | undefined {
    const extension = /\.([A-Za-z0-9]+)$/.exec(key)?.[1]?.toLowerCase()
    if (extension && EXTENSIONS[extension]) return EXTENSIONS[extension]
    if (key.toLowerCase() === "dockerfile") return "dockerfile"
    if (SQL_KEYS.has(key)) return "sql"
    if (key === "commands" || key === "beforeCommands") return taskType?.includes(".powershell.") ? "powershell" : "shell"
    if (key === "script" && taskType) {
        for (const [family, language] of SCRIPT_FAMILIES) {
            const match = family.exec(taskType)
            if (match) return language.replace("$1", match[1] ?? "")
        }
    }
    return undefined
}

const JSON_OPENING = /^(\{\s*("|\}|$)|\[\s*([[{"\d\]-]|true|false|null|$))/

function looksLikeJson(lines: string[]) {
    return JSON_OPENING.test(lines.find(line => line.trim())?.trim() ?? "")
}

/** The `type` of the mapping whose keys start at `column`, searched from `line` up to the mapping's first line and down to its end. */
function taskTypeAround(lines: string[], line: number, column: number): string | undefined {
    const sibling = (i: number) => {
        const match = KEY.exec(lines[i]!)
        return match && match[1]!.length + (match[2]?.length ?? 0) === column ? match : undefined
    }
    for (let i = line; i >= 0; i--) {
        const text = lines[i]!
        if (!text.trim()) continue
        if (indentOf(text) < column && !sibling(i)) break
        const type = sibling(i) && TYPE.exec(text)
        if (type) return type[2]
        if (sibling(i)?.[2]) break
    }
    for (let i = line + 1; i < lines.length; i++) {
        const text = lines[i]!
        if (!text.trim() || indentOf(text) > column) continue
        if (indentOf(text) < column || !sibling(i) || sibling(i)?.[2]) break
        const type = TYPE.exec(text)
        if (type) return type[2]
    }
    return undefined
}

/**
 * The block scalars of a YAML document that hold code, and their language: the `resolve` answer first (a plugin
 * schema's `$language`), then a file name's extension, the property names and task families plugins use, and JSON
 * for content opening with `{` or `[`. Line-based and linear in the size of the document.
 */
export function embeddedBlocks(yaml: string, resolve?: LanguageResolver): EmbeddedBlock[] {
    const lines = yaml.split("\n")
    const blocks: EmbeddedBlock[] = []
    for (let i = 0; i < lines.length; i++) {
        const header = HEADER.exec(lines[i]!)
        const item = header ? undefined : ITEM_HEADER.exec(lines[i]!)
        if (!header && !item) continue

        let key: string | undefined
        let keyColumn: number
        let contentAbove: number
        let keyLine = i
        if (header) {
            key = header[3] ?? header[4] ?? header[5]!.trim()
            keyColumn = header[1]!.length + (header[2]?.length ?? 0)
            contentAbove = keyColumn
        } else {
            contentAbove = item![1]!.length
            keyLine = i - 1
            while (keyLine >= 0 && (!lines[keyLine]!.trim() || indentOf(lines[keyLine]!) >= contentAbove)) keyLine--
            const parentKey = keyLine >= 0 ? KEY.exec(lines[keyLine]!) : null
            key = parentKey?.[3]?.trim()
            keyColumn = parentKey ? parentKey[1]!.length + (parentKey[2]?.length ?? 0) : contentAbove
        }

        let end = i
        for (let j = i + 1; j < lines.length && (!lines[j]!.trim() || indentOf(lines[j]!) > contentAbove); j++) {
            if (lines[j]!.trim()) end = j
        }
        if (end === i || !key) {
            i = end
            continue
        }
        const content = lines.slice(i + 1, end + 1)
        const taskType = taskTypeAround(lines, keyLine, keyColumn)
        const language = resolve?.(taskType, key) ?? defaultLanguage(taskType, key) ?? (looksLikeJson(content) ? "json" : undefined)
        if (language) {
            const indent = Math.min(...content.filter(line => line.trim()).map(indentOf))
            blocks.push({startLine: i + 1, endLine: end, indent, language})
        }
        i = end
    }
    return blocks
}
