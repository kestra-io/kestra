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

type RenderVariant = "default" | "enhanced";

interface RenderOptions {
    onlyLink?: boolean;
    permalink?: boolean;
    html?: boolean;
    variant?: RenderVariant;
}

export async function render(markdown: string, options: RenderOptions = {}) {
    const markdownWithAlerts = typeof markdown === "string"
        ? markdown
            .replace(
                /(\n)?::\s*alert\{type="(.*?)"\}\s*\n([\s\S]*?)\n::\s*(\n)?/g,
                (_: string, newLine1: string, type: string, content: string, newLine2: string) => 
                    `${newLine1 ?? ""}::: ${type}\n${content}\n:::${newLine2 ?? ""}`
            )
            .replace(
                /::\s*alert\{type="(.*?)"\}\s*([^\n]*)\s*::/g,
                (_: string, type: string, content: string) => 
                    `::: ${type}\n${content}\n:::`
            )
        : markdown;

    const {createHighlighterCore, githubDark, githubLight, markdownIt, mark, meta, mila, anchor, container, fromHighlighter, linkTag, langs, onigurumaEngine} = await import("./markdownDeps")
    const highlighter = await getHighlighter(createHighlighterCore as any, Object.values(langs), onigurumaEngine, githubDark, githubLight);

    if(githubDark["colors"] && githubLight["colors"]) {
        githubDark["colors"]["editor.background"] = "var(--bs-gray-500)";
        githubLight["colors"]["editor.background"] = "var(--bs-white)";
    }

    const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

    const variant: RenderVariant = options.variant ?? "default";

    let md;
    if (options.onlyLink) {
        md = new markdownIt("zero");
        md.enable(["link", "linkify", "entity", "html_inline"]);
    } else {
        md = new markdownIt();
    }

    md.use(mark)
        .use(meta)
        .use(mila, {matcher: (href: string) => href.match(/^https?:\/\//), attrs: {target: "_blank", rel: "noopener noreferrer"}})
        .use(anchor, {permalink: options.permalink ? anchor.permalink.ariaHidden({placement: "before"}) : undefined})
        .use(container, "warning")
        .use(container, "info")
        .use(container, "danger")
        .use(container, "success")
        .use(container, "tip")
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

    if (variant === "enhanced") {
        applyEnhancedRenderers(md);
    } else {
        md.renderer.rules.table_open = () => "<table class=\"table\">\n";
    }
    return md.render(markdownWithAlerts);
}

function applyEnhancedRenderers(md: any) {
    const defaultHeadingOpen = md.renderer.rules.heading_open?.bind(md.renderer.rules) ?? ((tokens: any, idx: number, options: any, env: any, self: any) => self.renderToken(tokens, idx, options));
    md.renderer.rules.heading_open = (tokens: any, idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx];
        const level = typeof token.tag === "string" && /^h\d$/i.test(token.tag) ? Number(token.tag.substring(1)) : null;
        token.attrJoin("class", "doc-heading");
        if (level) {
            token.attrJoin("class", `doc-heading--level-${level}`);
        }
        return defaultHeadingOpen(tokens, idx, options, env, self);
    };

    const defaultTableOpen = md.renderer.rules.table_open?.bind(md.renderer.rules) ?? ((tokens: any, idx: number, options: any, env: any, self: any) => self.renderToken(tokens, idx, options));
    md.renderer.rules.table_open = (tokens: any, idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx];
        token.attrSet("class", "doc-table");
        token.attrJoin("data-enhanced", "true");
        return defaultTableOpen(tokens, idx, options, env, self);
    };

    const defaultFence = md.renderer.rules.fence?.bind(md.renderer.rules) ?? ((tokens: any, idx: number, options: any, env: any, self: any) => self.renderToken(tokens, idx, options));

    md.renderer.rules.fence = (tokens: any, idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx];
        const info = token.info ? md.utils.unescapeAll(token.info).trim() : "";
        const langName = info.split(/\s+/g)[0] || "text";
        const codeId = `code-${idx}-${Math.random().toString(36).slice(2, 10)}`;
        const highlighted = defaultFence(tokens, idx, options, env, self);
        const enriched = typeof highlighted === "string"
            ? highlighted.replace("<pre", `<pre id="${codeId}"`)
            : highlighted;

        return `
<div class="doc-code-block" data-language="${langName.toLowerCase()}">
  <div class="doc-code-toolbar">
    <span class="doc-code-language">${langName.toUpperCase()}</span>
    <button type="button" class="doc-copy-button"
            data-copy-target="${codeId}" aria-label="Copy code block">
        <span class="doc-copy-label">Copy</span>
    </button>
  </div>
  ${enriched}
</div>`;
    };
}
