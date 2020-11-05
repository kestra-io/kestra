import markdownIt from "markdown-it";
import mark from "markdown-it-mark";
import meta from "markdown-it-meta";
import anchor from "markdown-it-anchor";

export default class Markdown {
    static render(markdown, options) {
        options = options || {}

        // noinspection JSPotentiallyInvalidConstructorUsage
        let md = new markdownIt() // jshint ignore:line
            .use(mark)
            .use(meta)
            .use(anchor, {
                permalink: options.permalink || false,
                permalinkBefore: options.permalink || false
            })

        md.set({
            html: true,
            xhtmlOut: true,
            breaks: true,
            linkify: true,
            typographer: true,
            langPrefix: "language-",
            quotes: "“”‘’",
        })

        md.renderer.rules.table_open = () => "<table class=\"table table-bordered\">\n"

        return md.render(
            markdown
        );
    }
}
