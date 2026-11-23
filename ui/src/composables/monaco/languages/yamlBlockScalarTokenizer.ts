import * as monaco from "monaco-editor/editor/editor.api"
import type {languages} from "monaco-editor/editor/editor.api"

type MonarchRules = languages.IMonarchLanguageRule[]

interface LanguageWithLoader extends languages.ILanguageExtensionPoint {
    loader?: () => Promise<{language?: languages.IMonarchLanguage}>
}

/**
 * Monaco's YAML tokenizer only keeps a block scalar (`|` or `>`) highlighted as a string while every
 * line has exactly the same indentation as the first one. A more indented line ends the block and is
 * highlighted as YAML. In YAML, any line indented at least as much as the first one is part of the block.
 *
 * Returns a copy of the language where a line continues the block when it starts with the block indentation
 * (`$S2`, captured on the first line), followed by anything.
 */
export function withIndentedBlockScalarContent(language: languages.IMonarchLanguage): languages.IMonarchLanguage {
    if (language.tokenizer?.multiStringContinued === undefined) {
        return language
    }

    const continued: MonarchRules = [
        [
            /^( *).+$/,
            {
                cases: {
                    "$1~$S2 *": "string",
                    "@default": {token: "@rematch", next: "@popall"},
                },
            },
        ],
    ]

    return {
        ...language,
        tokenizer: {...language.tokenizer, multiStringContinued: continued},
    }
}

/** Replaces Monaco's built-in tokenizer for `languageId` with one that keeps indented block scalar content as a string. */
export async function registerIndentedBlockScalarTokenizer(languageId: string): Promise<void> {
    const definition = monaco.languages.getLanguages().find(l => l.id === languageId) as LanguageWithLoader | undefined
    const loaded = await definition?.loader?.()
    if (loaded?.language === undefined) {
        return
    }

    monaco.languages.setMonarchTokensProvider(languageId, withIndentedBlockScalarContent(loaded.language))
}
