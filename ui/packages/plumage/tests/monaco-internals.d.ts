declare module "monaco-editor/editor/standalone/common/monarch/monarchCompile.js" {
    export function compile(languageId: string, language: unknown): unknown
}

declare module "monaco-editor/editor/standalone/common/monarch/monarchLexer.js" {
    interface State {
        readonly __state: unique symbol
    }
    export class MonarchTokenizer {
        constructor(languageService: unknown, themeService: unknown, languageId: string, lexer: unknown, configurationService: unknown)
        getInitialState(): State
        tokenize(line: string, hasEOL: boolean, state: State): {tokens: {offset: number, type: string}[], endState: State}
    }
}

declare module "monaco-editor/editor/common/languages/supports/tokenization.js" {
    export class TokenTheme {
        static createFromRawTokenTheme(rules: {token: string, foreground?: string, background?: string, fontStyle?: string}[], customTokenColors: string[]): TokenTheme
        getColorMap(): {toString(): string}[]
        match(languageId: number, token: string): number
    }
}

declare module "monaco-editor/editor/common/encodedTokenAttributes.js" {
    export const TokenMetadata: {
        getForeground(metadata: number): number
        getFontStyle(metadata: number): number
    }
}
