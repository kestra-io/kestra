// Only the grammars the app itself renders are in this chunk; every other language is a
// separate chunk fetched on demand by loadLanguageOnDemand().
import {createHighlighterCore, isSpecialLang, type HighlighterCore} from "shiki/core"

export type {HighlighterCore}
export {isSpecialLang}

/**
 * Module-level singleton for the Shiki highlighter, shared by every KsMarkdown
 * instance and by the app's plugin-schema code blocks, so one registry and one
 * chunk serve both.
 */
let promise: Promise<HighlighterCore> | null = null

import {createJavaScriptRegexEngine} from "shiki/engine/javascript"

import GithubLight from "shiki/themes/github-light.mjs"
import GithubDark from "shiki/themes/github-dark.mjs"
import Json from "shiki/langs/json.mjs"
import Python from "shiki/langs/python.mjs"
import Yaml from "shiki/langs/yaml.mjs"

export function getShiki(): Promise<HighlighterCore> {
    if (!promise) {

        promise = (async () => {
            const jsEngine = createJavaScriptRegexEngine()

            return createHighlighterCore({
                themes: [GithubDark, GithubLight],
                langs: [
                    Json,
                    Python,
                    Yaml,
                ],
                engine: jsEngine,
            })
        })()
    }
    return promise
}

let bundledLanguages: Promise<Record<string, any>> | null = null

/**
 * Registers a grammar that is not pre-registered above. Shiki's langs index is a list of
 * per-language dynamic imports, so this costs that index once plus the one grammar.
 */
export async function loadLanguageOnDemand(highlighter: HighlighterCore, lang: string): Promise<boolean> {
    // text/plaintext/plain/txt/ansi render without a grammar and are absent from the
    // index, so fetching it for one of them would download it to change nothing.
    if (isSpecialLang(lang)) {
        return true
    }

    bundledLanguages ??= import("shiki/langs")
        .then((module) => module.bundledLanguages)
        // Drop the memo on failure: a cached rejection would fail every later load.
        .catch(() => {
            bundledLanguages = null
            return {}
        })

    const loader = (await bundledLanguages)[lang]
    if (!loader) {
        return false
    }

    try {
        await highlighter.loadLanguage(loader)
        return true
    } catch {
        return false
    }
}
