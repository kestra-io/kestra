// shiki/core keeps the grammars below as the only ones in this chunk; every
// other language is fetched on demand by loadLanguageOnDemand().
import {createHighlighterCore} from "shiki/core"

/**
 * Module-level singleton for the Shiki highlighter.
 * Created once and shared across all KsMarkdown instances.
 */
let promise: Promise<any> | null = null

import {createJavaScriptRegexEngine} from "shiki/engine/javascript"

import GithubLight from "shiki/themes/github-light.mjs"
import GithubDark from "shiki/themes/github-dark.mjs"
import Bash from "shiki/langs/bash.mjs"
import C from "shiki/langs/c.mjs"
import Cpp from "shiki/langs/cpp.mjs"
import Csv from "shiki/langs/csv.mjs"
import Dockerfile from "shiki/langs/dockerfile.mjs"
import Go from "shiki/langs/go.mjs"
import Groovy from "shiki/langs/groovy.mjs"
import Handlebars from "shiki/langs/handlebars.mjs"
import Hcl from "shiki/langs/hcl.mjs"
import Ini from "shiki/langs/ini.mjs"
import Java from "shiki/langs/java.mjs"
import Javascript from "shiki/langs/javascript.mjs"
import Json from "shiki/langs/json.mjs"
import Markdown from "shiki/langs/markdown.mjs"
import Mermaid from "shiki/langs/mermaid.mjs"
import Perl from "shiki/langs/perl.mjs"
import Php from "shiki/langs/php.mjs"
import Python from "shiki/langs/python.mjs"
import R from "shiki/langs/r.mjs"
import Ruby from "shiki/langs/ruby.mjs"
import Rust from "shiki/langs/rust.mjs"
import Scala from "shiki/langs/scala.mjs"
import Sql from "shiki/langs/sql.mjs"
import Systemd from "shiki/langs/systemd.mjs"
import Twig from "shiki/langs/twig.mjs"
import Typescript from "shiki/langs/typescript.mjs"
import Xml from "shiki/langs/xml.mjs"
import Yaml from "shiki/langs/yaml.mjs"
import Html from "shiki/langs/html.mjs"

export function getShiki(): Promise<any> {
    if (!promise) {

        promise = (async () => {
            const jsEngine = createJavaScriptRegexEngine()

            return createHighlighterCore({
                themes: [GithubDark, GithubLight],
                langs: [
                    Bash,
                    C,
                    Cpp,
                    Csv,
                    Dockerfile,
                    Go,
                    Groovy,
                    Handlebars,
                    Hcl,
                    Ini,
                    Java,
                    Javascript,
                    Json,
                    Markdown,
                    Mermaid,
                    Perl,
                    Php,
                    Python,
                    R,
                    Ruby,
                    Rust,
                    Scala,
                    Sql,
                    Systemd,
                    Twig,
                    Typescript,
                    Xml,
                    Yaml,
                    Html,
                ],
                engine: jsEngine,
            })
        })()
    }
    return promise
}

let bundledLanguages: Promise<Record<string, any>> | null = null

/**
 * Registers a grammar that is not pre-registered above, from Shiki's full
 * bundle (one extra chunk, fetched once). Returns false for unknown languages.
 */
export async function loadLanguageOnDemand(highlighter: any, lang: string): Promise<boolean> {
    bundledLanguages ??= import("shiki/langs").then((module) => module.bundledLanguages)

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
