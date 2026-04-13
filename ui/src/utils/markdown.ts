import {HighlighterCoreOptions, LanguageRegistration, RegexEngine, ThemeRegistrationRaw, HighlighterGeneric} from "shiki/core";
import xss, {escapeAttrValue} from "xss";

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
    showCopyButtons?: boolean;
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
        applyEnhancedRenderers(md, options.showCopyButtons ?? true);
    } else {
        md.renderer.rules.table_open = () => "<table class=\"table\">\n";
    }
    const rendered = md.render(markdownWithAlerts);

    if (options.html) {
        return xss(rendered, {
            whiteList: {
                a: ["href", "title", "target", "rel", "id", "class"],
                abbr: ["title"],
                article: ["class", "role"],
                b: [], i: [], em: [], strong: [], del: [], s: [],
                blockquote: ["class"],
                br: [],
                code: ["class"],
                dd: [], dl: [], dt: [],
                details: ["open", "class"],
                div: ["class", "id", "role", "style"],
                h1: ["id", "class"], h2: ["id", "class"], h3: ["id", "class"],
                h4: ["id", "class"], h5: ["id", "class"], h6: ["id", "class"],
                hr: [],
                img: ["src", "alt", "title", "width", "height", "class"],
                kbd: [],
                li: ["class"], ol: ["start", "class"], ul: ["class"],
                mark: [],
                p: ["class"],
                pre: ["class", "id"],
                section: ["class"],
                small: [],
                span: ["class", "style"],
                sub: [], sup: [],
                summary: ["class"],
                table: ["class"], thead: [], tbody: [], tr: [], th: ["class", "align"], td: ["class", "align"],
                var: [],
                "router-md": ["execution", "namespace", "flowId"],
                video: ["src", "controls", "width", "height", "class"],
                source: ["src", "type"],
                button: ["type", "class", "aria-label"],
            },
            stripIgnoreTag: true,
            onIgnoreTagAttr: function (_tag, name, value) {
                if (name.startsWith("data-")) {
                    return name + "=\"" + escapeAttrValue(value) + "\"";
                }
                if (name.startsWith("aria-")) {
                    return name + "=\"" + escapeAttrValue(value) + "\"";
                }
                return undefined;
            },
        });
    }

    return rendered;
}

function applyEnhancedRenderers(md: any, showCopyButtons: boolean) {
    const defaultHeadingOpen = md.renderer.rules.heading_open?.bind(md.renderer.rules) ?? ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options));
    md.renderer.rules.heading_open = (tokens: any, idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx];
        const level = typeof token.tag === "string" && /^h\d$/i.test(token.tag) ? Number(token.tag.substring(1)) : null;
        token.attrJoin("class", "doc-heading");
        if (level) {
            token.attrJoin("class", `doc-heading--level-${level}`);
        }
        return defaultHeadingOpen(tokens, idx, options, env, self);
    };

    const defaultTableOpen = md.renderer.rules.table_open?.bind(md.renderer.rules) ?? ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options));
    md.renderer.rules.table_open = (tokens: any, idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx];
        token.attrSet("class", "doc-table");
        token.attrJoin("data-enhanced", "true");
        return defaultTableOpen(tokens, idx, options, env, self);
    };

    const defaultFence = md.renderer.rules.fence?.bind(md.renderer.rules) ?? ((tokens: any, idx: number, options: any, _env: any, self: any) => self.renderToken(tokens, idx, options));

    md.renderer.rules.fence = (tokens: any, idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx];
        const info = token.info ? md.utils.unescapeAll(token.info).trim() : "";
        const langName = info.split(/\s+/g)[0] || "text";
        const codeId = `code-${idx}-${Math.random().toString(36).slice(2, 10)}`;
        const highlighted = defaultFence(tokens, idx, options, env, self);
        const enriched = typeof highlighted === "string"
            ? highlighted.replace("<pre", `<pre id="${codeId}"`)
            : highlighted;

        const copyButton = showCopyButtons
            ? `<button type="button" class="doc-copy-button"
            data-copy-target="${codeId}" aria-label="Copy code block">
        <span class="doc-copy-label">Copy</span>
    </button>`
            : "";

        return `
<div class="doc-code-block" data-language="${langName.toLowerCase()}">
  <div class="doc-code-toolbar">
    <span class="doc-code-language">${langName.toUpperCase()}</span>
    ${copyButton}
  </div>
  ${enriched}
</div>`;
    };
}
