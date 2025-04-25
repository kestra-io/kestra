import {HighlighterCoreOptions, LanguageRegistration, RegexEngine, ThemeRegistrationRaw, HighlighterGeneric} from "shiki/core";

let highlighter: Promise<HighlighterGeneric<"yaml"| "python" | "javascript", "github-dark" | "github-light">> | null = null;

async function getHighlighter(
    createHighlighterCore: (options: HighlighterCoreOptions<false>) => Promise<HighlighterGeneric<"yaml"| "python" | "javascript", "github-dark" | "github-light">>,
    langs: LanguageRegistration[][],
    engine: Promise<RegexEngine>,
    githubDark: ThemeRegistrationRaw,
    githubLight: ThemeRegistrationRaw){
    if (!highlighter) {
        highlighter = createHighlighterCore({
            langs,
            themes: [githubDark, githubLight],
            engine
        });
    }
    return highlighter;
}

export async function render(markdown: string, options: {onlyLink?: boolean, permalink?: boolean, html?: boolean} = {}) {
    const {createHighlighterCore, githubDark, githubLight, markdownIt, mark, meta, mila, anchor, container, fromHighlighter, linkTag, langs, onigurumaEngine} = await import( "./markdownDeps")
    const highlighter = await getHighlighter(createHighlighterCore as any, langs, onigurumaEngine, githubDark, githubLight);

    if(githubDark["colors"] && githubLight["colors"]) {
        githubDark["colors"]["editor.background"] = "var(--bs-gray-500)";
        githubLight["colors"]["editor.background"] = "var(--bs-white)";
    }

    const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

    let md;
    if (options.onlyLink) {
        md = new markdownIt("zero");
        md.enable(["link", "linkify", "entity", "html_inline"]);
    } else {
        md = new markdownIt();
    }

    md.use(mark)
        .use(meta)
        .use(mila, {matcher: (href) => href.match(/^https?:\/\//), attrs: {target: "_blank", rel: "noopener noreferrer"}})
        .use(anchor, {permalink: options.permalink ? anchor.permalink.ariaHidden({placement: "before"}) : undefined})
        .use(container, "warning")
        .use(container, "info")
        .use(fromHighlighter(highlighter, {theme: darkTheme ? "github-dark" : "github-light"}))
        .use(linkTag);

    md.set({
        html: options.html,
        xhtmlOut: true,
        breaks: true,
        linkify: true,
        typographer: true,
        langPrefix: "language-",
        quotes: "“”‘’",
    });

    md.renderer.rules.table_open = () => "<table class=\"table\">\n";

    return md.render(markdown);
}
