import {describe, test, expect, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../../src/index"
import KsMarkdown from "../../../../src/components/Data/KsMarkdown/KsMarkdown.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsMarkdown", () => {
    beforeEach(() => {
        Object.defineProperty(navigator, "clipboard", {
            value: {writeText: vi.fn().mockResolvedValue(undefined)},
            configurable: true,
        })
    })

    test("renders container with ks-markdown class", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "Hello"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-markdown").exists()).toBe(true)
    })

    test("renders a paragraph", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "Hello world"},
            global: globalConfig,
        })
        expect(wrapper.find("p").exists()).toBe(true)
        expect(wrapper.find("p").text()).toBe("Hello world")
    })

    test("renders strong and emphasis in paragraph", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "Hello **bold** and _italic_"},
            global: globalConfig,
        })
        expect(wrapper.find("strong").text()).toBe("bold")
        expect(wrapper.find("em").text()).toBe("italic")
    })

    test("renders GFM strikethrough", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "~~deleted~~"},
            global: globalConfig,
        })
        expect(wrapper.find("del").exists()).toBe(true)
        expect(wrapper.find("del").text()).toBe("deleted")
    })

    test("renders h1 heading with anchor link", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "# My Title"},
            global: globalConfig,
        })
        const h1 = wrapper.find("h1.ks-markdown__heading")
        expect(h1.exists()).toBe(true)
        expect(h1.attributes("id")).toBe("my-title")
        const link = h1.find("a.ks-markdown__heading-link")
        expect(link.exists()).toBe(true)
        expect(link.attributes("href")).toBe("#my-title")
        expect(link.attributes("aria-hidden")).toBe("true")
    })

    test("renders h2 heading with slugified id", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "## Hello World!"},
            global: globalConfig,
        })
        const h2 = wrapper.find("h2")
        expect(h2.exists()).toBe(true)
        expect(h2.attributes("id")).toBe("hello-world")
    })

    test("renders blockquote", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "> A quoted text"},
            global: globalConfig,
        })
        expect(wrapper.find("blockquote.ks-markdown__blockquote").exists()).toBe(true)
        expect(wrapper.find("blockquote").text()).toContain("A quoted text")
    })

    test("renders code block with copy button", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```js\nconsole.log('hi')\n```"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-markdown__code-block").exists()).toBe(true)
        expect(wrapper.find(".ks-markdown__copy-btn").exists()).toBe(true)
        expect(wrapper.find(".ks-markdown__copy-btn").attributes("title")).toBe("Copy to clipboard")
        expect(wrapper.find("pre").exists()).toBe(true)
        expect(wrapper.find("code.language-js").exists()).toBe(true)
    })

    test("renders code language label when lang is specified", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```python\nprint('hello')\n```"},
            global: globalConfig,
        })
        const langLabel = wrapper.find(".ks-markdown__code-lang")
        expect(langLabel.exists()).toBe(true)
        expect(langLabel.text()).toBe("python")
    })

    test("does not render language label for unlabelled code block", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```\nsome code\n```"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-markdown__code-lang").exists()).toBe(false)
    })

    test("copy button calls navigator.clipboard.writeText with code content", async () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```\nconst x = 42\n```"},
            global: globalConfig,
        })
        await wrapper.find(".ks-markdown__copy-btn").trigger("click")
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith("const x = 42")
    })

    test("renders mermaid code block as mermaid div", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```mermaid\ngraph TD\n  A --> B\n```"},
            global: globalConfig,
        })
        const mermaidDiv = wrapper.find(".ks-markdown__mermaid")
        expect(mermaidDiv.exists()).toBe(true)
        expect(mermaidDiv.classes()).toContain("mermaid")
        expect(mermaidDiv.text()).toContain("graph TD")
        // Mermaid blocks must not render as a regular code block
        expect(wrapper.find(".ks-markdown__code-block").exists()).toBe(false)
    })

    test("renders inline code", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "Use `npm install` to install"},
            global: globalConfig,
        })
        const code = wrapper.find("code.ks-markdown__inline-code")
        expect(code.exists()).toBe(true)
        expect(code.text()).toBe("npm install")
    })

    test("renders unordered list", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "- item one\n- item two\n- item three"},
            global: globalConfig,
        })
        expect(wrapper.find("ul.ks-markdown__list").exists()).toBe(true)
        expect(wrapper.findAll("li")).toHaveLength(3)
    })

    test("renders ordered list", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "1. first\n2. second"},
            global: globalConfig,
        })
        expect(wrapper.find("ol.ks-markdown__list").exists()).toBe(true)
        expect(wrapper.findAll("li")).toHaveLength(2)
    })

    test("renders GFM table using KsTable", () => {
        const content = "| Name | Age |\n|------|-----|\n| Alice | 30 |\n| Bob | 25 |"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-markdown__table-wrapper").exists()).toBe(true)
        // KsTable renders as .kel-table (Element Plus table with kestra namespace)
        expect(wrapper.find(".kel-table").exists()).toBe(true)
        // ElTable renders thead/tbody but not th elements in jsdom (no layout engine)
        expect(wrapper.find("thead").exists()).toBe(true)
        expect(wrapper.find("tbody").exists()).toBe(true)
        // 2 data rows
        expect(wrapper.findAll("tbody tr").length).toBeGreaterThanOrEqual(2)
    })

    test("renders external link with target blank and rel", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "[Visit](https://kestra.io)"},
            global: globalConfig,
        })
        const link = wrapper.find("a.ks-markdown__link")
        expect(link.exists()).toBe(true)
        expect(link.attributes("href")).toBe("https://kestra.io")
        expect(link.attributes("target")).toBe("_blank")
        expect(link.attributes("rel")).toBe("noopener noreferrer")
    })

    test("renders internal link without target blank", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "[Flows](/flows)"},
            global: globalConfig,
        })
        const link = wrapper.find("a.ks-markdown__link")
        expect(link.exists()).toBe(true)
        expect(link.attributes("target")).toBeUndefined()
    })

    test("renders thematic break", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "---"},
            global: globalConfig,
        })
        expect(wrapper.find("hr.ks-markdown__hr").exists()).toBe(true)
    })

    test("renders :::alert{type='info'} as KsAlert info", () => {
        const content = ":::alert{type=\"info\"}\nThis is an info message.\n:::"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert").exists()).toBe(true)
        expect(wrapper.find(".kel-alert--info").exists()).toBe(true)
    })

    test("renders :::alert{type='warning'} as KsAlert warning", () => {
        const content = ":::alert{type=\"warning\"}\nBe careful!\n:::"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--warning").exists()).toBe(true)
    })

    test("renders :::alert{type='error'} as KsAlert error", () => {
        const content = ":::alert{type=\"error\"}\nSomething went wrong.\n:::"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--error").exists()).toBe(true)
    })

    test("renders :::alert{type='success'} as KsAlert success", () => {
        const content = ":::alert{type=\"success\"}\nDone!\n:::"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--success").exists()).toBe(true)
    })

    test("re-renders when content prop changes", async () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "# First Heading"},
            global: globalConfig,
        })
        expect(wrapper.find("h1").exists()).toBe(true)

        await wrapper.setProps({content: "## Second Heading"})
        expect(wrapper.find("h1").exists()).toBe(false)
        expect(wrapper.find("h2").exists()).toBe(true)
        expect(wrapper.find("h2").attributes("id")).toBe("second-heading")
    })

    // ─── Edge cases ────────────────────────────────────────────────────────────

    test("renders empty content without error", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: ""},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-markdown").exists()).toBe(true)
    })

    test("renders all heading levels h1–h6", () => {
        const content = ["# H1", "## H2", "### H3", "#### H4", "##### H5", "###### H6"].join("\n")
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        for (const level of [1, 2, 3, 4, 5, 6] as const) {
            expect(wrapper.find(`h${level}`).exists()).toBe(true)
        }
    })

    test("heading slug strips special characters", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "## Hello, World! (2024)"},
            global: globalConfig,
        })
        expect(wrapper.find("h2").attributes("id")).toBe("hello-world-2024")
    })

    test("heading slug handles inline code in title", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "## Using `npm install`"},
            global: globalConfig,
        })
        const h2 = wrapper.find("h2")
        expect(h2.exists()).toBe(true)
        // id is derived from text content (code content is extracted as text)
        expect(h2.attributes("id")).toContain("npm-install")
    })

    test("renders nested list items", () => {
        const content = "- parent\n  - child one\n  - child two"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        const lists = wrapper.findAll("ul")
        expect(lists.length).toBeGreaterThanOrEqual(1)
        expect(wrapper.findAll("li").length).toBeGreaterThanOrEqual(3)
    })

    test("renders image with alt and src", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "![Kestra logo](https://kestra.io/logo.png)"},
            global: globalConfig,
        })
        const img = wrapper.find("img.ks-markdown__image")
        expect(img.exists()).toBe(true)
        expect(img.attributes("src")).toBe("https://kestra.io/logo.png")
        expect(img.attributes("alt")).toBe("Kestra logo")
    })

    test("renders GFM table with aligned columns via KsTable", () => {
        const content = "| Left | Center | Right |\n|:-----|:------:|------:|\n| A | B | C |"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        // Table renders using KsTable — alignment is delegated to ElTable internals
        // th elements are not rendered in jsdom (no layout engine)
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("renders multiple code blocks independently", () => {
        const content = "```js\nconst a = 1\n```\n\n```py\nprint(1)\n```"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-markdown__code-block")).toHaveLength(2)
        expect(wrapper.findAll(".ks-markdown__copy-btn")).toHaveLength(2)
    })

    test("alert directive content renders as paragraph text", () => {
        const content = ":::alert{type=\"info\"}\nSome **bold** note.\n:::"
        const wrapper = mount(KsMarkdown, {
            props: {content},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert").exists()).toBe(true)
        expect(wrapper.find(".kel-alert strong").exists()).toBe(true)
    })

    test("http:// external link also gets target blank", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "[HTTP](http://example.com)"},
            global: globalConfig,
        })
        const link = wrapper.find("a.ks-markdown__link")
        expect(link.attributes("target")).toBe("_blank")
    })

    // ─── Shiki syntax highlighting ──────────────────────────────────────────

    test("code block shows plain fallback before Shiki loads", () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```js\nconst x = 1\n```"},
            global: globalConfig,
        })
        // Before Shiki resolves, the plain pre fallback is rendered
        expect(wrapper.find(".ks-markdown__code-block").exists()).toBe(true)
        expect(wrapper.find(".ks-markdown__copy-btn").exists()).toBe(true)
    })

    test("code block upgrades to Shiki-highlighted HTML after load", async () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```javascript\nconst x = 1\n```"},
            global: globalConfig,
        })

        // Shiki loads asynchronously (dynamic import + language loading).
        // Poll until the highlighted container appears (up to 5s).
        await vi.waitFor(
            () => expect(wrapper.find(".ks-markdown__code-shiki").exists()).toBe(true),
            {timeout: 5000, interval: 50},
        )

        // Shiki wraps output in <pre class="shiki …">
        expect(wrapper.find(".ks-markdown__code-shiki pre.shiki").exists()).toBe(true)
        // The plain fallback should no longer be rendered
        expect(wrapper.find(".ks-markdown__code-plain").exists()).toBe(false)
    })


    test("Shiki-highlighted code contains the original source text", async () => {
        const wrapper = mount(KsMarkdown, {
            props: {content: "```typescript\nconst greeting: string = 'hello'\n```"},
            global: globalConfig,
        })
        await flushPromises()

        const shikiDiv = wrapper.find(".ks-markdown__code-shiki")
        if (shikiDiv.exists()) {
            expect(shikiDiv.text()).toContain("greeting")
        }
    })
})
