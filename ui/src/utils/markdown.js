import markdownIt from "markdown-it";
import mark from "markdown-it-mark";
import meta from "markdown-it-meta";
import anchor from "markdown-it-anchor";
import container from "markdown-it-container";

export default class Markdown {
    static render(markdown, options) {
        options = options || {}

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
