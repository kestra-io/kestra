<template>
    <div class="ks-markdown" :class="{'ks-markdown--compact': compact}">
        <component :is="renderContent()" />
    </div>
</template>

<script setup lang="ts">
    import {Fragment, computed, h, onMounted, ref, shallowRef, useId, watch, type Component, type VNode} from "vue"
    import {useI18n} from "vue-i18n"
    import {unified} from "unified"
    import remarkDirective from "remark-directive"
    import remarkFrontmatter from "remark-frontmatter"
    import remarkGfm from "remark-gfm"
    import remarkParse from "remark-parse"
    import type {Root, RootContent} from "mdast"
    import type {ThemedTokenWithVariants} from "@shikijs/types"
    import xss, {escapeAttrValue} from "xss"
    import Check from "vue-material-design-icons/Check.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import KsAlert from "../../Feedback/KsAlert.vue"
    import KsTable from "../KsTable/KsTable.vue"
    import KsTableColumn from "../KsTable/KsTableColumn.vue"
    import {useTheme} from "../../../composables/useTheme"
    import {copyToClipboard} from "../../../utils/clipboard"
    import {getShiki, loadLanguageOnDemand} from "./shikiHighlighter"

    type Rendered = VNode | string

    interface CodeBlock {
        lang: string
        value: string
    }

    const props = withDefaults(
        defineProps<{
            content?: string
            html?: boolean
            xssProtection?: boolean
            /** Scales headings down to the UI type scale, for markdown rendered inside a panel rather than as a page. */
            compact?: boolean
            /** Tag name → component, for HTML elements (`a`, `img`) and PascalCase custom tags (`ChildCard`). */
            components?: Record<string, Component>
        }>(),
        {
            content: undefined,
            html: true,
            xssProtection: true,
            compact: false,
            components: () => ({}),
        },
    )

    const OPENING_TAG = /^<([A-Za-z][\w-]*)(\s[^>]*)?>$/

    /** Raw HTML embeds in docs are limited to YouTube videos and Arcade demos, so any other iframe host is dropped. */
    const IFRAME_HOSTS = ["www.youtube.com", "www.youtube-nocookie.com", "demo.arcade.software"]

    /**
     * Markdown links bypass the HTML whitelist, so their scheme is checked here.
     * `data:` is excluded: a navigated `data:` document executes, unlike an image.
     */
    const URL_SCHEMES = ["http:", "https:", "mailto:", "tel:", "ftp:"]

    /** Only base64 `image/*` data URLs, which browsers render with scripts disabled. */
    const DATA_IMAGE = /^data:image\/[a-z0-9.+-]+;base64,[a-z0-9+/]+=*$/i

    const WHITELIST = {
        a: ["href", "title", "target", "rel", "id", "class"],
        abbr: ["title"],
        article: ["class", "role"],
        b: [],
        blockquote: ["class"],
        br: [],
        button: ["type", "class", "aria-label"],
        code: ["class"],
        dd: [],
        del: [],
        details: ["open", "class"],
        div: ["class", "id", "role", "style"],
        dl: [],
        dt: [],
        em: [],
        h1: ["id", "class"],
        h2: ["id", "class"],
        h3: ["id", "class"],
        h4: ["id", "class"],
        h5: ["id", "class"],
        h6: ["id", "class"],
        hr: [],
        i: [],
        iframe: ["src", "title", "width", "height", "allow", "allowfullscreen", "referrerpolicy", "frameborder", "class"],
        img: ["src", "alt", "title", "width", "height", "class"],
        kbd: [],
        li: ["class"],
        mark: [],
        ol: ["start", "class"],
        p: ["class"],
        pre: ["class", "id"],
        s: [],
        section: ["class"],
        small: [],
        source: ["src", "type"],
        span: ["class", "style"],
        strong: [],
        sub: [],
        summary: ["class"],
        sup: [],
        table: ["class"],
        tbody: [],
        td: ["class", "align"],
        th: ["class", "align"],
        thead: [],
        tr: [],
        ul: ["class"],
        var: [],
        video: ["src", "controls", "width", "height", "class"],
    }

    const {t} = useI18n({useScope: "global"})
    const {isDark} = useTheme()
    const uid = useId()

    const parser = unified()
        .use(remarkParse)
        .use(remarkFrontmatter)
        .use(remarkGfm)
        .use(remarkDirective)

    const ast = computed(() => parser.parse(props.content ?? "") as Root)
    const blocks = computed(() => collectCodeBlocks(ast.value.children))
    const highlights = shallowRef(new Map<string, ThemedTokenWithVariants[][]>())
    const diagrams = shallowRef(new Map<string, string>())
    const copied = ref<string>()
    const slugs = new Map<string, number>()

    function renderContent(): VNode {
        slugs.clear()

        return h(Fragment, renderNodes(ast.value.children))
    }

    function collectCodeBlocks(nodes: RootContent[], found: CodeBlock[] = []): CodeBlock[] {
        for (const node of nodes) {
            if (node.type === "code") {
                found.push({lang: node.lang ?? "", value: node.value})
            }

            if ("children" in node) {
                collectCodeBlocks(node.children, found)
            }
        }

        return found
    }

    function extractText(nodes: RootContent[]): string {
        return nodes
            .map((node): string => {
                if ("children" in node) {
                    return extractText(node.children)
                }

                return "value" in node ? node.value : ""
            })
            .join("")
    }

    /**
     * Keeps relative URLs and allowed schemes. Whitespace and control characters are
     * ignored while matching, so `java\tscript:` cannot slip through.
     */
    function sanitizeUrl(url: unknown): string | undefined {
        if (typeof url !== "string" || !url.trim()) {
            return undefined
        }

        const value = url.trim()
        const compacted = Array.from(value).filter((char) => char.charCodeAt(0) > 0x20).join("")
        const scheme = compacted.match(/^([a-z][a-z0-9+.-]*):/i)
        if (!scheme) {
            return value
        }

        return URL_SCHEMES.includes(`${scheme[1].toLowerCase()}:`) ? value : undefined
    }

    function sanitizeImageSrc(url: unknown): string | undefined {
        if (typeof url === "string" && DATA_IMAGE.test(url.trim())) {
            return url.trim()
        }

        return sanitizeUrl(url)
    }

    function sanitizeHtml(content: string): string {
        return xss(content, {
            whiteList: WHITELIST,
            stripIgnoreTag: true,
            onTagAttr: (tag: string, name: string, value: string) => {
                if (tag !== "iframe" || name !== "src") {
                    return undefined
                }

                try {
                    const url = new URL(value)

                    return url.protocol === "https:" && IFRAME_HOSTS.includes(url.hostname) ? undefined : ""
                } catch {
                    return ""
                }
            },
            onIgnoreTagAttr: (_tag: string, name: string, value: string) => {
                if (name.startsWith("data-") || name.startsWith("aria-")) {
                    return `${name}="${escapeAttrValue(value)}"`
                }

                return undefined
            },
        })
    }

    function parseAttributes(source: string): Record<string, string> {
        const attributes: Record<string, string> = {}
        for (const [, name, double, single, bare] of source.matchAll(/(\w[\w-]*)(?:=(?:"([^"]*)"|'([^']*)'|(\S+)))?/g)) {
            attributes[name] = double ?? single ?? bare ?? ""
        }

        return attributes
    }

    /** Custom components skip the HTML whitelist, so event handlers, styles and unsafe URLs are dropped here. */
    function componentAttributes(source: string): Record<string, string | undefined> {
        const attributes = parseAttributes(source)
        if (!props.xssProtection) {
            return attributes
        }

        return Object.fromEntries(
            Object.entries(attributes)
                .filter(([name]) => !/^(on|style$)/i.test(name))
                .map(([name, value]) => {
                    switch (name.toLowerCase()) {
                    case "href":
                        return [name, sanitizeUrl(value)]
                    case "src":
                        return [name, sanitizeImageSrc(value)]
                    default:
                        return [name, value]
                    }
                }),
        )
    }

    function renderRawHtml(html: string): VNode {
        return h("span", {innerHTML: props.xssProtection ? sanitizeHtml(html) : html})
    }

    /** Inline HTML reaches the AST one tag per node, so an opening tag is paired with its sibling closing tag. */
    function findClosingTag(nodes: RootContent[], start: number): number {
        const opening = nodes[start]
        const tag = opening.type === "html" ? opening.value.match(OPENING_TAG)?.[1] : undefined
        if (!tag || !(props.html || props.components[tag])) {
            return -1
        }

        const name = tag.toLowerCase()
        let depth = 0
        for (let index = start + 1; index < nodes.length; index++) {
            const node = nodes[index]
            if (node.type !== "html") {
                continue
            }

            if (node.value.match(OPENING_TAG)?.[1].toLowerCase() === name) {
                depth++
            } else if (node.value.trim().toLowerCase() === `</${name}>`) {
                if (depth === 0) {
                    return index
                }

                depth--
            }
        }

        return -1
    }

    function renderElement(openingTag: string, children: RootContent[]): Rendered[] {
        const [, tag, attributes = ""] = openingTag.match(OPENING_TAG) ?? []
        const component = props.components[tag]
        if (component) {
            return [h(component, componentAttributes(attributes), {default: () => renderNodes(children)})]
        }

        const allowed = (props.xssProtection ? sanitizeHtml(openingTag) : openingTag).match(OPENING_TAG)
        if (!allowed) {
            return renderNodes(children)
        }

        return [h(allowed[1], parseAttributes(allowed[2] ?? ""), renderNodes(children))]
    }

    function renderNodes(nodes: RootContent[]): Rendered[] {
        const result: Rendered[] = []
        for (let index = 0; index < nodes.length; index++) {
            const node = nodes[index]
            const closing = findClosingTag(nodes, index)
            if (node.type === "html" && closing > index) {
                result.push(...renderElement(node.value, nodes.slice(index + 1, closing)))
                index = closing
                continue
            }

            const vnode = renderNode(node)
            if (vnode !== null) {
                result.push(vnode)
            }
        }

        return result
    }

    function renderNode(node: RootContent): Rendered | null {
        switch (node.type) {
        case "text":
            return node.value

        case "paragraph":
            return h("p", renderNodes(node.children))

        case "heading": {
            const base = extractText(node.children)
                .toLowerCase()
                .trim()
                .replace(/[^\p{L}\p{N}\s_-]/gu, "")
                .replace(/[\s_]+/g, "-")
                .replace(/^-+|-+$/g, "")
            const count = slugs.get(base) ?? 0
            const slug = count ? `${base}-${count}` : base
            slugs.set(base, count + 1)

            return h(`h${node.depth}`, {id: slug || undefined, class: "heading"}, [
                ...renderNodes(node.children),
                slug ? h("a", {href: `#${slug}`, class: "anchor", "aria-hidden": "true", tabindex: "-1"}, "#") : null,
            ])
        }

        case "blockquote":
            return h("blockquote", renderNodes(node.children))

        case "code": {
            const lang = node.lang ?? ""
            const value = node.value
            if (lang === "mermaid") {
                const svg = diagrams.value.get(`${isDark.value}::${value}`)

                return svg ? h("div", {class: "diagram", innerHTML: svg}) : h("pre", {class: "diagram"}, value)
            }

            const key = `${lang}::${value}`
            const theme = isDark.value ? "dark" : "light"
            const label = t("ks_markdown.copy")
            const tokens = highlights.value.get(key)?.flatMap((line, index) => [
                ...(index ? ["\n"] : []),
                ...line.map(({content, variants}) => h("span", {style: {color: variants[theme]?.color}}, content)),
            ])

            return h("div", {class: "code-block"}, [
                lang ? h("span", {class: "lang"}, lang) : null,
                h(
                    "button",
                    {
                        class: ["copy", {copied: copied.value === key}],
                        type: "button",
                        title: label,
                        "aria-label": label,
                        onClick: () => copyToClipboard(value)
                            .then(() => {
                                copied.value = key
                                setTimeout(() => {
                                    if (copied.value === key) {
                                        copied.value = undefined
                                    }
                                }, 2000)
                            })
                            .catch(() => undefined),
                    },
                    [h(ContentCopy), h(Check, {class: "check"})],
                ),
                h("pre", [h("code", tokens ?? value)]),
            ])
        }

        case "inlineCode":
            return h("code", node.value)

        case "list":
            return h(node.ordered ? "ol" : "ul", {
                start: node.ordered && node.start !== 1 ? node.start : undefined,
            }, renderNodes(node.children))

        case "listItem":
            return h("li", node.children.flatMap((child) => {
                return renderNodes(child.type === "paragraph" ? child.children : [child])
            }))

        case "table": {
            const [header, ...rows] = node.children
            const cells = rows.map((row) => row.children.map((cell) => renderNodes(cell.children)))
            const columns = header.children.map((cell, column) => h(KsTableColumn, {
                label: extractText(cell.children),
                align: node.align?.[column] ?? undefined,
            }, {
                default: ({$index}: {$index: number}) => cells[$index]?.[column] ?? [],
            }))

            return h("div", {class: "table"}, [
                h(KsTable, {data: cells}, {default: () => columns}),
            ])
        }

        case "link": {
            const href = sanitizeUrl(node.url)
            const title = node.title ?? undefined
            if (props.components.a) {
                return h(props.components.a, {href, title, class: "link"}, {
                    default: () => renderNodes(node.children),
                })
            }

            const external = href !== undefined && /^https?:\/\//.test(href)

            return h("a", {
                href,
                title,
                class: "link",
                target: external ? "_blank" : undefined,
                rel: external ? "noopener noreferrer" : undefined,
            }, renderNodes(node.children))
        }

        case "image": {
            const src = sanitizeImageSrc(node.url)
            const alt = node.alt ?? ""
            if (props.components.img) {
                return h(props.components.img, {src, alt})
            }

            return h("img", {src, alt, title: node.title ?? undefined})
        }

        case "strong":
            return h("strong", renderNodes(node.children))

        case "emphasis":
            return h("em", renderNodes(node.children))

        case "delete":
            return h("del", renderNodes(node.children))

        case "break":
            return h("br")

        case "thematicBreak":
            return h("hr")

        case "html": {
            const match = node.value.trim().match(/^<([A-Za-z][A-Za-z0-9]*)(\s[^>]*)?>(?:([\s\S]*?)<\/\1>)?$/)
            const component = match ? props.components[match[1]] : undefined
            if (match && component) {
                const [, , attributes = "", inner = ""] = match

                return h(component, componentAttributes(attributes.trim()), inner.trim()
                    ? {default: () => [renderRawHtml(inner)]}
                    : undefined)
            }

            return props.html ? renderRawHtml(node.value) : h("span", node.value)
        }

        case "containerDirective":
            if (node.name !== "alert") {
                return h("div", {class: "directive"}, renderNodes(node.children))
            }

            return h(KsAlert, {
                type: (node.attributes?.type ?? "info") as "success" | "warning" | "info" | "error",
                showIcon: false,
                class: "alert",
            }, {default: () => renderNodes(node.children)})

        case "leafDirective":
        case "textDirective":
            return null

        default:
            return "children" in node && Array.isArray(node.children) ? h("div", renderNodes(node.children)) : null
        }
    }

    async function highlight(codeBlocks: CodeBlock[]) {
        if (!codeBlocks.length) {
            return
        }

        const highlighter = await getShiki()
        const loaded = new Set(highlighter.getLoadedLanguages() as string[])
        const unavailable = new Set<string>()
        const missing = [...new Set(codeBlocks.map(({lang}) => lang))].filter((lang) => lang && !loaded.has(lang))
        await Promise.all(missing.map(async (lang) => {
            if (!await loadLanguageOnDemand(highlighter, lang)) {
                unavailable.add(lang)
            }
        }))

        const result = new Map<string, ThemedTokenWithVariants[][]>()
        for (const {lang, value} of codeBlocks) {
            const key = `${lang}::${value}`
            if (highlights.value.has(key) || result.has(key)) {
                continue
            }

            try {
                result.set(key, highlighter.codeToTokensWithThemes(value, {
                    lang: !lang || unavailable.has(lang) ? "text" : lang,
                    themes: {light: "github-light", dark: "github-dark"},
                }))
            } catch {
                continue
            }
        }

        if (result.size) {
            highlights.value = new Map([...highlights.value, ...result])
        }
    }

    async function renderDiagrams(sources: string[]) {
        const dark = isDark.value
        const missing = [...new Set(sources)].filter((source) => !diagrams.value.has(`${dark}::${source}`))
        if (!missing.length) {
            return
        }

        const {default: mermaid} = await import("mermaid")
        mermaid.initialize({startOnLoad: false, theme: dark ? "dark" : "default", suppressErrorRendering: true})

        const result = new Map<string, string>()
        for (const [index, source] of missing.entries()) {
            try {
                const {svg} = await mermaid.render(`${uid}-mermaid-${index}`, source)
                result.set(`${dark}::${source}`, svg)
            } catch {
                continue
            }
        }

        if (result.size) {
            diagrams.value = new Map([...diagrams.value, ...result])
        }
    }

    watch(blocks, (codeBlocks) => {
        void highlight(codeBlocks.filter(({lang}) => lang !== "mermaid"))
    }, {immediate: true})

    /** Started once mounted, when `useTheme` has read the real theme, so a dark page renders its diagrams once. */
    onMounted(() => {
        watch([blocks, isDark], ([codeBlocks]) => {
            void renderDiagrams(codeBlocks.filter(({lang}) => lang === "mermaid").map(({value}) => value))
        }, {immediate: true})
    })
</script>

<style scoped>
    :where(.ks-markdown) {
        color: var(--ks-text-primary);
        line-height: var(--ks-line-height-loose);
        overflow-wrap: break-word;
    }

    :where(.ks-markdown) > :last-child {
        margin-bottom: 0;
    }

    :where(.ks-markdown) {
        & p {
            margin: 0 0 var(--ks-spacing-4);

            &:last-child {
                margin-bottom: 0;
            }
        }

        & .heading {
            position: relative;
            margin: var(--ks-spacing-5) 0 var(--ks-spacing-2);
            font-weight: var(--ks-font-weight-semibold);
            line-height: var(--ks-line-height-tight);
            color: var(--ks-text-primary);
            scroll-margin-top: var(--ks-spacing-4);

            &:first-child {
                margin-top: 0;
            }

            &:hover .anchor {
                opacity: 1;
            }
        }

        & h1 {
            font-size: var(--ks-font-size-4xl);
        }

        & h2 {
            font-size: var(--ks-font-size-3xl);
        }

        & h3 {
            font-size: var(--ks-font-size-2xl);
        }

        & h4 {
            font-size: var(--ks-font-size-xl);
        }

        & h5 {
            font-size: var(--ks-font-size-lg);
        }

        & h6 {
            font-size: var(--ks-font-size-base);
        }

        &.ks-markdown--compact {
            & h1 {
                font-size: var(--ks-font-size-lg);
            }

            & h2 {
                font-size: var(--ks-font-size-base);
            }

            & h3, & h4, & h5, & h6 {
                font-size: var(--ks-font-size-sm);
            }
        }

        & .anchor {
            margin-left: var(--ks-spacing-2);
            opacity: 0;
            color: var(--ks-text-dim);
            font-weight: var(--ks-font-weight-regular);
            text-decoration: none;
            transition: opacity 0.15s ease;
        }

        & .link {
            color: var(--ks-text-link);
            text-decoration: none;

            &:hover {
                text-decoration: underline;
            }
        }

        & :not(pre) > code {
            padding: 0.125rem var(--ks-spacing-1);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
            background-color: var(--ks-bg-elevated);
            color: var(--ks-text-link);
            font-size: 0.875em;
        }

        & pre {
            margin: 0;
            padding: var(--ks-spacing-3);
            overflow-x: auto;
            background: none;
            font-size: var(--ks-font-size-sm);
        }

        & ul,
        & ol {
            margin: 0 0 var(--ks-spacing-4);
            padding-left: var(--ks-spacing-5);

            & ul,
            & ol {
                margin: 0;
            }
        }

        & li {
            margin: var(--ks-spacing-1) 0;
        }

        & blockquote {
            margin: 0 0 var(--ks-spacing-4);
            padding: var(--ks-spacing-2) var(--ks-spacing-4);
            border-left: 4px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
            background-color: var(--ks-bg-elevated);
            color: var(--ks-text-secondary);
        }

        & .code-block {
            position: relative;
            margin: 0 0 var(--ks-spacing-4);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-lg);
            background-color: var(--ks-bg-input);
            overflow: hidden;

            &:hover,
            &:focus-within {
                & .lang {
                    opacity: 0;
                }

                & .copy {
                    opacity: 1;
                    pointer-events: auto;
                }
            }
        }

        & .lang {
            position: absolute;
            top: var(--ks-spacing-1);
            right: var(--ks-spacing-1);
            padding: var(--ks-spacing-1);
            font-family: var(--ks-font-family-mono);
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-dim);
            transition: opacity 0.15s ease;
        }

        & .copy {
            position: absolute;
            top: var(--ks-spacing-1);
            right: var(--ks-spacing-1);
            display: grid;
            place-items: center;
            padding: var(--ks-spacing-1);
            border: 0;
            border-radius: var(--ks-radius-base);
            background: var(--ks-bg-base);
            color: var(--ks-text-dim);
            cursor: pointer;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.15s ease;

            &:hover {
                color: var(--ks-text-primary);
            }

            & > * {
                grid-area: 1 / 1;
                transition: opacity 0.15s ease;
            }

            & .check {
                color: var(--ks-text-success);
                opacity: 0;
            }

            &.copied {
                & > * {
                    opacity: 0;
                }

                & .check {
                    opacity: 1;
                }
            }
        }

        & .table {
            margin: 0 0 var(--ks-spacing-4);
            overflow-x: auto;
        }

        & img {
            max-width: 100%;
            height: auto;
        }

        & hr {
            margin: var(--ks-spacing-5) 0;
            border: none;
            border-top: 1px solid var(--ks-border-default);
        }

        & .diagram {
            margin: 0 0 var(--ks-spacing-4);
        }

        & div.diagram {
            text-align: center;
        }

        & .alert {
            margin: 0 0 var(--ks-spacing-4);
        }
    }
</style>
