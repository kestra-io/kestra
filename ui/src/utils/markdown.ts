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

const ALLOWED_URL_SCHEMES = ["http:", "https:", "mailto:", "tel:", "ftp:"];

// markdown-it's own denylist let these through, so inline images already embedded in descriptions
// keep rendering. It deliberately excludes svg+xml, which can carry script.
const ALLOWED_DATA_URL = /^data:image\/(?:gif|png|jpeg|webp);/i;

/**
 * Decides whether a Markdown-native link or image URL may keep its target.
 *
 * markdown-it only denies a handful of schemes, so anything it has not heard of — and any scheme
 * split by a control character, such as {@code java<tab>script:} — reaches the DOM as a live
 * anchor and runs in the viewer's session. Only the schemes above, base64 raster images, and
 * relative URLs, fragments and query-only links are kept; everything else is rejected, which
 * makes markdown-it fall back to literal text so no anchor or image is rendered at all.
 */
export function isSafeUrl(url: string): boolean {
    const value = url.trim();
    if (!value) return true;

    // markdown-it validates the already-normalized URL, so a smuggled control character arrives
    // percent-encoded; both forms are dropped before the scheme is matched.
    const compacted = Array.from(value)
        .filter((char) => char.charCodeAt(0) > 0x20)
        .join("")
        .replace(/%(?:0[0-9a-f]|1[0-9a-f]|20)/gi, "");

    const scheme = compacted.match(/^([a-z][a-z0-9+.-]*):/i);
    if (!scheme) return true;

    return ALLOWED_URL_SCHEMES.includes(scheme[1].toLowerCase() + ":") || ALLOWED_DATA_URL.test(compacted);
}

interface RenderOptions {
    onlyLink?: boolean;
    permalink?: boolean;
    html?: boolean;
    variant?: RenderVariant;
    showCopyButtons?: boolean;
    linkify?: boolean;
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

    md.validateLink = isSafeUrl;

    md.set({
        html: options.html,
        xhtmlOut: true,
        breaks: true,
        linkify: options.linkify ?? true,
        typographer: true,
        langPrefix: "language-",
        quotes: "“”‘’",
    });

    if (variant === "enhanced") {
        applyEnhancedRenderers(md, options.showCopyButtons ?? true);
    } else {
        md.renderer.rules.table_open = () => "<table class=\"table\">\n";
    }
    return md.render(markdownWithAlerts);
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
