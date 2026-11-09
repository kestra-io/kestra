import {createHighlighterCore, type HighlighterCore, type LanguageInput} from "shiki/core"
import {createJavaScriptRegexEngine} from "shiki/engine/javascript"
import {createOnigurumaEngine} from "shiki/engine/oniguruma"
import {themes, toTextMateTheme} from "../src/index.ts"
import {pebbleInjection} from "../src/pebble.ts"
import {probeTheme} from "./probe.ts"

export type Engine = "javascript" | "oniguruma"

export const LANGS: Record<string, LanguageInput> = {
    yaml: import("shiki/langs/yaml.mjs"),
    bash: import("shiki/langs/bash.mjs"),
    python: import("shiki/langs/python.mjs"),
    json: import("shiki/langs/json.mjs"),
    java: import("shiki/langs/java.mjs"),
    hcl: import("shiki/langs/hcl.mjs"),
    sql: import("shiki/langs/sql.mjs"),
    javascript: import("shiki/langs/javascript.mjs"),
    typescript: import("shiki/langs/typescript.mjs"),
    go: import("shiki/langs/go.mjs"),
    groovy: import("shiki/langs/groovy.mjs"),
    dockerfile: import("shiki/langs/dockerfile.mjs"),
    powershell: import("shiki/langs/powershell.mjs"),
    markdown: import("shiki/langs/markdown.mjs"),
    twig: import("shiki/langs/twig.mjs"),
}

/** A Shiki core highlighter set up the way the docs site sets it up, optionally with the VS Code Pebble grammar. */
export async function highlighter(engine: Engine, {pebble = false, langs = Object.keys(LANGS)} = {}): Promise<HighlighterCore> {
    return createHighlighterCore({
        engine: engine === "javascript" ? createJavaScriptRegexEngine() : createOnigurumaEngine(import("shiki/wasm")),
        themes: [...themes, probeTheme].map(toTextMateTheme),
        langs: [...langs.map(lang => LANGS[lang]!), ...(pebble ? [pebbleInjection] : [])],
    })
}
