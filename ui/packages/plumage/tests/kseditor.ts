import {compile} from "monaco-editor/editor/standalone/common/monarch/monarchCompile.js"
import {MonarchTokenizer} from "monaco-editor/editor/standalone/common/monarch/monarchLexer.js"
import {MONACO_SCOPES} from "../src/monaco.ts"
import type {SyntaxRole} from "../src/index.ts"
import {monarchLanguages, type MonarchLanguage} from "./monarch.ts"

/** The MONACO_SCOPES entry Monaco's trie picks: the longest key that is a whole-segment prefix of the token. */
export function monacoEntry(token: string) {
    const parts = token.split(".")
    for (let n = parts.length; n > 0; n--) {
        const entry = MONACO_SCOPES.find(([key]) => key === parts.slice(0, n).join("."))
        if (entry) return entry
    }
    return undefined
}

export function monacoRole(token: string): SyntaxRole {
    const mapping = monacoEntry(token)?.[1]
    if (Array.isArray(mapping)) return mapping[0]
    return mapping === undefined || ["italic", "bold", "underline"].includes(mapping) ? "text" : mapping as SyntaxRole
}

/** Copy of KsEditor's `withIndentedBlockScalarContent` (ui/src/composables/monaco/languages/yamlBlockScalarTokenizer.ts). */
function withIndentedBlockScalarContent(language: MonarchLanguage): MonarchLanguage {
    return {
        ...language,
        tokenizer: {
            ...language.tokenizer,
            multiStringContinued: [[/^( *).+$/, {cases: {"$1~$S2 *": "string", "@default": {token: "@rematch", next: "@popall"}}}]],
        },
    }
}

/** Roles per character for YAML as KsEditor tokenizes it, with Monaco's own Monarch lexer. */
export async function ksEditorYamlRoles(): Promise<(code: string) => SyntaxRole[]> {
    const yaml = (await monarchLanguages()).get("yaml")![0]!
    const config = {getValue: () => 20_000, onDidChangeConfiguration: () => ({dispose() {}})}
    const tokenizer = new MonarchTokenizer({}, {}, "yaml", compile("yaml", withIndentedBlockScalarContent(yaml)), config)
    return code => {
        const roles: SyntaxRole[] = []
        let state = tokenizer.getInitialState()
        for (const line of code.split("\n")) {
            const {tokens, endState} = tokenizer.tokenize(line, true, state)
            tokens.forEach((token, i) => {
                const end = tokens[i + 1]?.offset ?? line.length
                for (let c = token.offset; c < end; c++) roles.push(monacoRole(token.type))
            })
            roles.push("text")
            state = endState
        }
        return roles
    }
}
