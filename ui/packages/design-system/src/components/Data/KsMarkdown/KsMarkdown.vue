<template>
    <div ref="containerRef" class="ks-markdown">
        <component :is="markdownContent" />
    </div>
</template>

<script setup lang="ts">
    import {computed, h, onMounted, onUpdated, ref, watch, type Component, type FunctionalComponent, type VNode} from "vue"
    import {unified} from "unified"
    import remarkParse from "remark-parse"
    import remarkFrontmatter from "remark-frontmatter"
    import remarkGfm from "remark-gfm"
    import remarkDirective from "remark-directive"
    import type {Root, RootContent} from "mdast"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import xss, {escapeAttrValue} from "xss"
    import KsAlert from "../../Feedback/KsAlert.vue"
    import KsTable from "../KsTable/KsTable.vue"
    import KsTableColumn from "../KsTable/KsTableColumn.vue"
    import {getShiki} from "./shikiHighlighter"

    const props = withDefaults(
        defineProps<{
            content?: string,
            html?: boolean;
            xssProtection?: boolean;
            /** Map of tag name → Vue component. Supports lowercase HTML elements (e.g. "a")
             *  and PascalCase custom components (e.g. "ChildCard"). */
            components?: Record<string, Component>;
        }>(),
        {
            content: undefined,
            html: true,
            xssProtection: true,
            components: () => ({}),
        },
    )

    const containerRef = ref<HTMLElement | null>(null)
    // Reactive cache: "lang::value" → Shiki-generated HTML
    const codeHighlights = ref<Map<string, string>>(new Map())

    const ast = computed<Root>(() => {
        return unified()
            .use(remarkParse)
            .use(remarkFrontmatter)
            .use(remarkGfm)
            .use(remarkDirective)
            .parse(props.content ?? "") as Root
    })

    function slugify(text: string): string {
        return text
            .toLowerCase()
            .trim()
            .replace(/[^\w\s-]/g, "")
            .replace(/[\s_]+/g, "-")
            .replace(/^-+|-+$/g, "")
    }

    function extractText(nodes: RootContent[]): string {
        return nodes.map((node): string => {
            // Leaf nodes carry their text in `value` (text, inlineCode, html, …)
            if ("value" in node && !("children" in node)) return (node as any).value as string
            if ("children" in node) return extractText((node as any).children as RootContent[])
            return ""
        }).join("")
    }

    function renderNodes(nodes: any[]): (VNode | string)[] {
        const result: (VNode | string)[] = []
        for (const node of nodes) {
            const vnode = renderNode(node)
            if (vnode !== null) {
                result.push(vnode)
            }
        }
        return result
    }

    function htmlEscape(content: string): string {
        return xss(content, {
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
            onIgnoreTagAttr: function (_tag: string, name: string, value: string) {
                if (name.startsWith("data-")) {
                    return name + "=\"" + escapeAttrValue(value) + "\""
                }
                if (name.startsWith("aria-")) {
                    return name + "=\"" + escapeAttrValue(value) + "\""
                }
                return undefined
            },
        })
    }

    function parseHtmlAttributes(attrsStr: string): Record<string, unknown> {
        const attrs: Record<string, unknown> = {}
        const re = /(\w[\w-]*)(?:=(?:"([^"]*)"|'([^']*)'|(\S+)))?/g
        let m: RegExpExecArray | null
        while ((m = re.exec(attrsStr)) !== null) {
            const key = m[1]
            attrs[key] = m[2] ?? m[3] ?? m[4] ?? true
        }
        return attrs
    }

    function tryRenderCustomComponent(html: string): VNode | null {
        if (!props.components || Object.keys(props.components).length === 0) return null

        // Match <ComponentName attrs>inner</ComponentName> or <ComponentName attrs></ComponentName>
        const match = html.trim().match(/^<([A-Za-z][A-Za-z0-9]*)(\s[^>]*)?>(?:([\s\S]*?)<\/\1>)?$/)
        if (!match) return null

        const [, tagName, attrsStr = "", innerHtml = ""] = match
        const component = props.components[tagName]
        if (!component) return null

        const attrs = parseHtmlAttributes(attrsStr.trim())
        const slots = innerHtml.trim()
            ? {default: () => [h("span", {innerHTML: innerHtml})]}
            : undefined
        return h(component as any, attrs, slots)
    }

    function renderNode(node: any): VNode | string | null {
        switch (node.type as string) {
        case "text":
            return node.value as string

        case "paragraph":
            return h("p", renderNodes(node.children))

        case "heading": {
            const text = extractText(node.children as RootContent[])
            const slug = slugify(text)
            const tag = `h${node.depth as number}` as "h1" | "h2" | "h3" | "h4" | "h5" | "h6"
            return h(tag, {id: slug, class: "ks-markdown__heading"}, [
                ...renderNodes(node.children),
                h("a", {
                    href: `#${slug}`,
                    class: "ks-markdown__heading-link",
                    "aria-hidden": "true",
                    tabindex: "-1",
                }, "#"),
            ])
        }

        case "blockquote":
            return h("blockquote", {class: "ks-markdown__blockquote"}, renderNodes(node.children))

        case "code": {
            const lang = (node.lang ?? "") as string
            const value = node.value as string

            if (lang === "mermaid") {
                return h("div", {class: "ks-markdown__mermaid mermaid"}, value)
            }

            const key = `${lang}::${value}`
            const highlightedHtml = codeHighlights.value.get(key)

            return h("div", {class: "ks-markdown__code-block"}, [
                h("div", {class: "ks-markdown__code-header"}, [
                    lang ? h("span", {class: "ks-markdown__code-lang"}, lang) : null,
                    h("button", {
                        class: "ks-markdown__copy-btn",
                        type: "button",
                        title: "Copy to clipboard",
                        onClick: (e: MouseEvent) => {
                            const btn = e.currentTarget as HTMLButtonElement
                            navigator.clipboard.writeText(value).then(() => {
                                btn.querySelector(".ks-markdown__copy-btn-ok")?.classList.add("opacity-100")
                                setTimeout(() => {
                                    btn.querySelector(".ks-markdown__copy-btn-ok")?.classList.remove("opacity-100")
                                }, 2000)
                            }).catch(() => { /* clipboard unavailable */ })
                        },
                    }, [h(CheckCircleOutline, {class: "ks-markdown__copy-btn-ok"}), h(ContentCopy)]),
                ]),
                highlightedHtml
                    ? h("div", {class: "ks-markdown__code-shiki", innerHTML: highlightedHtml})
                    : h("pre", {class: "ks-markdown__code-plain"}, [
                        h("code", {class: lang ? `language-${lang}` : undefined}, value),
                    ]),
            ])
        }

        case "inlineCode":
            return h("code", {class: "ks-markdown__inline-code"}, node.value as string)

        case "list":
            return h(node.ordered ? "ol" : "ul", {class: "ks-markdown__list"}, renderNodes(node.children))

        case "listItem": {
            const children = (node.children as any[]).flatMap((child: any): (VNode | string)[] => {
                if (child.type === "paragraph") return renderNodes(child.children)
                const vnode = renderNode(child)
                return vnode !== null ? [vnode] : []
            })
            return h("li", children)
        }

        case "table": {
            const align = node.align as (string | null)[] | null
            const [headerRow, ...bodyRows] = node.children as any[]

            // Column labels extracted from the header row
            const headers = (headerRow.children as any[]).map((cell: any) =>
                extractText(cell.children as RootContent[]),
            )

            // Pre-render all cell content: cellGrid[rowIdx][colIdx] = VNodes
            const cellGrid = (bodyRows as any[]).map((row: any) =>
                (row.children as any[]).map((cell: any) => renderNodes(cell.children)),
            )

            const data = cellGrid.map((_, i) => ({_idx: i}))

            const columns = headers.map((label: string, colIdx: number) => {
                const cellAlign = align?.[colIdx] ?? null
                return h(KsTableColumn, {
                    label,
                    // align is not in KsTableColumn's defineProps but is forwarded via $attrs
                    ...(cellAlign ? {align: cellAlign} : {}),
                } as any, {
                    // oxlint-disable-next-line no-underscore-dangle
                    default: ({row}: {row: {_idx: number}}) => cellGrid[row._idx]?.[colIdx] ?? [],
                })
            })

            return h("div", {class: "ks-markdown__table-wrapper"}, [
                h(KsTable, {data} as any, {default: () => columns}),
            ])
        }

        case "link": {
            const url = node.url as string
            if (props.components?.a) {
                return h(props.components.a as any, {
                    href: url,
                    title: node.title ?? undefined,
                    class: "ks-markdown__link",
                }, {default: () => renderNodes(node.children)})
            }

            const isExternal = url.startsWith("http://") || url.startsWith("https://")
            return h("a", {
                href: url,
                title: node.title ?? undefined,
                target: isExternal ? "_blank" : undefined,
                rel: isExternal ? "noopener noreferrer" : undefined,
                class: "ks-markdown__link",
            }, renderNodes(node.children))
        }

        case "image":
            if (props.components?.img) {
                return h(props.components.img as any, {
                    src: node.url as string,
                    alt: (node.alt ?? "") as string,
                })
            }

            return h("img", {
                src: node.url as string,
                alt: (node.alt ?? "") as string,
                title: node.title ?? undefined,
                class: "ks-markdown__image",
            })

        case "strong":
            return h("strong", renderNodes(node.children))

        case "emphasis":
            return h("em", renderNodes(node.children))

        case "delete":
            return h("del", renderNodes(node.children))

        case "break":
            return h("br")

        case "thematicBreak":
            return h("hr", {class: "ks-markdown__hr"})

        case "html": {
            const customVNode = tryRenderCustomComponent(node.value as string)
            if (customVNode) return customVNode

            if (props.html) {
                if (props.xssProtection) {
                    return h("span", {innerHTML: htmlEscape(node.value) as string, class: "ks-markdown__raw-html"})
                } else {
                    return h("span", {innerHTML: node.value as string, class: "ks-markdown__raw-html"})
                }
            } else {
                return h("span", {innerText: node.value, class: "ks-markdown__raw-html"})
            }
        }

        // remark-directive: :::name{attrs}\ncontent\n:::
        case "containerDirective": {
            const name = node.name as string
            if (name === "alert") {
                const type = (node.attributes as Record<string, string> | undefined)?.type ?? "info"
                return h(KsAlert, {
                    type: type as "success" | "warning" | "info" | "error",
                    showIcon: false,
                    class: "ks-markdown__alert",
                }, {default: () => renderNodes(node.children)})
            }
            return h("div", {class: `ks-markdown__directive ks-markdown__directive--${name}`},
                     renderNodes(node.children))
        }

        case "leafDirective":
        case "textDirective":
            return null

        default:
            if ("children" in node && Array.isArray(node.children)) {
                return h("div", renderNodes(node.children as any[]))
            }

            return null
        }
    }

    // Depends on both `ast` and `codeHighlights` — re-evaluates when either changes.
    const markdownContent = computed<FunctionalComponent>(() => {
        const children = renderNodes(ast.value.children as any[])
        return () => children
    })

    async function highlightAllCodeBlocks(root: Root) {
        const blocks: {lang: string; value: string}[] = []

        function collect(nodes: any[]) {
            for (const node of nodes) {
                if (node.type === "code" && node.lang !== "mermaid") {
                    blocks.push({lang: node.lang ?? "", value: node.value as string})
                }
                if (Array.isArray(node.children)) collect(node.children as any[])
            }
        }
        collect(root.children as any[])
        if (!blocks.length) return

        const hl = await getShiki()
        if (!hl) return

        const updated = new Map(codeHighlights.value)

        for (const block of blocks) {
            const key = `${block.lang}::${block.value}`
            if (updated.has(key)) continue

            let lang = block.lang
            if (lang && !(hl.getLoadedLanguages() as string[]).includes(lang)) {
                try {
                    await hl.loadLanguage(lang as any)
                } catch {
                    lang = ""
                }
            }

            try {
                const html = hl.codeToHtml(block.value, {
                    lang: lang || "text",
                    themes: {light: "github-light", dark: "github-dark"},
                    defaultColor: false,
                }) as string
                updated.set(key, html)
            } catch {
                // Keep the plain-text fallback for this block
            }
        }

        codeHighlights.value = updated
    }

    // Trigger highlighting whenever the parsed AST changes (also runs on first mount)
    watch(ast, (newAst) => { void highlightAllCodeBlocks(newAst) }, {immediate: true})

    async function initMermaid() {
        const container = containerRef.value
        if (!container) return
        const nodes = container.querySelectorAll<HTMLElement>(".ks-markdown__mermaid:not([data-processed])")
        if (!nodes.length) return
        try {
            const {default: mermaid} = await import("mermaid")
            mermaid.initialize({startOnLoad: false, theme: "default"})
            await mermaid.run({nodes})
        } catch {
            // Mermaid not available or failed to render
        }
    }

    onMounted(initMermaid)
    onUpdated(initMermaid)
</script>

<style lang="scss">
    // ─── Shiki light/dark theme switching ────────────────────────────────────
    // `defaultColor: false` emits CSS vars (--shiki-light, --shiki-dark, etc.)
    // on each token. The html.dark class (managed by the theme toggle) switches
    // token colors without needing !important on the inline styles.
    .ks-markdown__code-shiki {
        .shiki {
            margin: 0;
            padding: 2rem;
            overflow-x: auto;
            background-color: var(--kel-bg-color-overlay);
            border-radius: var(--kel-border-radius-base);

            span { color: var(--shiki-light); }

            html.dark & {
                span { color: var(--shiki-dark); }
            }

            & code .line {
                display: inline;
            }
        }
    }

    .ks-markdown {
        color: var(--ks-content-default);

        h1, h2, h3, h4, h5, h6 {
            &.ks-markdown__heading {
                position: relative;
                margin: 1.25rem 0 0.5rem;
                scroll-margin-top: 1rem;

                &:first-child {
                    margin-top: 0;
                }

                .ks-markdown__heading-link {
                    margin-left: 0.4rem;
                    opacity: 0;
                    color: var(--kel-text-color-placeholder);
                    text-decoration: none;
                    font-weight: normal;
                    transition: opacity 0.15s ease;
                }

                &:hover .ks-markdown__heading-link { opacity: 1; }
            }
        }

        p {
            margin: 0.75rem 0;
            &:first-child { margin-top: 0; }
            &:last-child { margin-bottom: 0; }
        }

        .ks-markdown__blockquote {
            margin: 1rem 0;
            padding: 0.5rem 1rem;
            border-left: 4px solid var(--ks-border-primary);
            color: var(--ks-content-secondary);
            background-color: var(--kel-bg-color-overlay);
            border-radius: var(--kel-border-radius-base);
        }

        .ks-markdown__code-block {
            margin: 1rem 0;
            border: 1px solid var(--ks-border-primary);
            border-radius: var(--kel-border-radius-round);
            overflow: hidden;
            position: relative;

            .ks-markdown__code-header {
                position: absolute;
                display: flex;
                width: 100%;
                align-items: center;
                justify-content: flex-end;
                padding: 4px 8px;
                background-color: var(--ks-background-tertiary);
                gap: 8px;
                font-size: var(--ks-font-size-xs);
                font-family: var(--kel-font-family-monospace), monospace;
                color: var(--kel-text-color-placeholder);

                .ks-markdown__code-lang {
                    flex: 1;
                }

                .ks-markdown__copy-btn {
                    padding-right: 0;
                    right: -2px;
                    top: 2px;
                    position: relative;
                    border: 0;
                    background: var(--ks-background-default);
                    cursor: pointer;
                    color: var(--kel-text-color-placeholder);

                    &:hover {
                        color: var(--kel-text-color-primary);
                    }

                    .ks-markdown__copy-btn-ok {
                        transition: opacity 0.15s ease;
                        margin-right: 0.25rem;
                        color: var(--ks-content-success);
                        opacity: 0;
                    }
                }
            }

            .ks-markdown__code-plain {
                margin: 0;
                padding: 1rem;
                overflow-x: auto;
                background-color: var(--kel-bg-color-overlay);

                code {
                    font-family: var(--kel-font-family-monospace), monospace;
                    font-size: var(--ks-font-size-sm);
                    background: none;
                    padding: 0;
                    border: none;
                }
            }
        }

        .ks-markdown__inline-code {
            padding: 0.15rem 0.4rem;
            border-radius: var(--kel-border-radius-base);
            background-color: var(--kel-bg-color-overlay);
            border: 1px solid var(--ks-border-primary);
        }

        .ks-markdown__list {
            margin: 0.75rem 0;
            padding-left: 1.5rem;

            li { margin: 0.25rem 0; }
        }

        .ks-markdown__table-wrapper {
            margin: 1rem 0;
            overflow-x: auto;
        }

        .ks-markdown__link {
            color: var(--ks-content-link);
            text-decoration: none;

            &:hover { text-decoration: underline; }
        }

        .ks-markdown__image {
            max-width: 100%;
            height: auto;
        }

        .ks-markdown__hr {
            margin: 1.5rem 0;
            border: none;
            border-top: 1px solid var(--ks-border-primary);
        }

        .ks-markdown__mermaid {
            margin: 1rem 0;
            text-align: center;
        }

        .ks-markdown__alert {
            margin: 1rem 0;
        }
    }
</style>
