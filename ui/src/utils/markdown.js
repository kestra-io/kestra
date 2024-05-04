import markdownIt from "markdown-it";
import mark from "markdown-it-mark";
import meta from "markdown-it-meta";
import anchor from "markdown-it-anchor";
import container from "markdown-it-container";
import {fromHighlighter} from "@shikijs/markdown-it/core"
import {getHighlighterCore} from "shiki/core"
import githubDark from "shiki/themes/github-dark.mjs";
import githubLight from "shiki/themes/github-light.mjs";

githubDark["colors"]["editor.background"] = "var(--bs-gray-500)";
githubLight["colors"]["editor.background"] = "var(--bs-white)";

const highlighter = await getHighlighterCore({
    themes: [
        githubDark,
        githubLight
    ],
    langs: [
        import("shiki/langs/yaml.mjs"),
    ],
    loadWasm: import("shiki/wasm")
})

export default class Markdown {
    static async render(markdown, options) {
        options = options || {}

        const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

        // noinspection JSPotentiallyInvalidConstructorUsage
        let md = new markdownIt() // jshint ignore:line
            .use(mark)
            .use(meta)
            .use(anchor, {
                permalink: options.permalink ? anchor.permalink.ariaHidden({
                    placement: "before"
                }) : undefined
            })
            // if more alert types are used inside the task documentation, they need to be configured here also
            .use(container, "warning")
            .use(container, "info")
            .use(fromHighlighter(highlighter, {
                theme: darkTheme ? "github-dark" : "github-light",
            }));

        md.set({
            html: true,
            xhtmlOut: true,
            breaks: true,
            linkify: true,
            typographer: true,
            langPrefix: "language-",
            quotes: "“”‘’",
        })

        md.renderer.rules.table_open = () => "<table class=\"table\">\n"

        return md.render(
            markdown
        );
    }
}
