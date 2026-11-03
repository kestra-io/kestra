import {describe, test, expect, vi} from "vitest"
import {defineComponent, h, markRaw} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/units/i18nMount"
import KsAlert from "../../Feedback/KsAlert.vue"
import KsTable from "../KsTable/KsTable.vue"
import KsMarkdown from "./KsMarkdown.vue"
import {loadLanguageOnDemand} from "./shikiHighlighter"

vi.mock("./shikiHighlighter", async (importOriginal) => {
    const actual = await importOriginal<typeof import("./shikiHighlighter")>()
    return {...actual, loadLanguageOnDemand: vi.fn(actual.loadLanguageOnDemand)}
})

const BASE64_SVG = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjwvc3ZnPg=="

describe("KsMarkdown", () => {
    test("adds a hidden anchor link to each heading", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "# My Title"}})
        const anchor = wrapper.find("h1#my-title a.anchor")

        expect(anchor.attributes("href")).toBe("#my-title")
        expect(anchor.attributes("aria-hidden")).toBe("true")
    })

    test("derives unique heading ids from the heading text", () => {
        const wrapper = i18nMount(KsMarkdown, {
            props: {content: "# Hello, World! (2024)\n\n## Using `npm install`\n\n# 日本語の見出し\n\n# Setup\n\n# Setup"},
        })

        expect(wrapper.findAll(".heading").map((heading) => heading.attributes("id"))).toEqual([
            "hello-world-2024",
            "using-npm-install",
            "日本語の見出し",
            "setup",
            "setup-1",
        ])
    })

    test("re-renders when the content changes", async () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "# First"}})
        await wrapper.setProps({content: "## Second"})

        expect(wrapper.find("h1").exists()).toBe(false)
        expect(wrapper.find("h2").text()).toContain("Second")
    })

    test("keeps the start number of an ordered list", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "3. third\n4. fourth"}})

        expect(wrapper.find("ol").attributes("start")).toBe("3")
    })

    test("renders a GFM table through KsTable", async () => {
        const wrapper = i18nMount(KsMarkdown, {
            props: {content: "| Name | Age |\n|------|-----|\n| Alice | 30 |\n| Bob | `25` |"},
        })
        await flushPromises()

        expect(wrapper.findComponent(KsTable).exists()).toBe(true)
        expect(wrapper.find("tbody").text()).toContain("Alice")
        expect(wrapper.find("tbody code").text()).toBe("25")
    })

    test("renders the alert directive as a KsAlert with markdown inside", () => {
        const wrapper = i18nMount(KsMarkdown, {
            props: {content: ":::alert{type=\"warning\"}\nSome **bold** note.\n:::"},
        })
        const alert = wrapper.findComponent(KsAlert)

        expect(alert.props("type")).toBe("warning")
        expect(alert.find("strong").text()).toBe("bold")
    })

    test("opens only external links in a new tab", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "[docs](https://kestra.io) [flows](/flows)"}})
        const [external, internal] = wrapper.findAll("a")

        expect(external.attributes("target")).toBe("_blank")
        expect(external.attributes("rel")).toBe("noopener noreferrer")
        expect(internal.attributes("target")).toBeUndefined()
    })

    test.each([
        ["https://kestra.io", "https://kestra.io"],
        ["mailto:hello@kestra.io", "mailto:hello@kestra.io"],
        ["#section", "#section"],
        ["javascript:alert(1)", undefined],
        ["<JaVa\tScRiPt:alert(1)>", undefined],
        ["data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", undefined],
    ])("keeps only allowed link targets: %s", (url, href) => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: `[link](${url})`}})

        expect(wrapper.find("a").attributes("href")).toBe(href)
    })

    test.each([
        ["https://kestra.io/logo.png", "https://kestra.io/logo.png"],
        [BASE64_SVG, BASE64_SVG],
        ["javascript:alert(1)", undefined],
        ["data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", undefined],
        ["data:image/svg+xml,<svg>evil</svg>", undefined],
    ])("keeps only safe image sources: %s", (url, src) => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: `![alt](${url})`}})
        const image = wrapper.find("img")

        expect(image.attributes("src")).toBe(src)
        expect(image.attributes("alt")).toBe("alt")
    })

    test("renders a code block with its language and a labelled copy button", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```js\nconsole.log('hi')\n```"}})

        expect(wrapper.find(".lang").text()).toBe("js")
        expect(wrapper.find(".copy").attributes("aria-label")).toBe("ks_markdown.copy")
        expect(wrapper.find("pre code").text()).toBe("console.log('hi')")
    })

    test("copies the code block source", async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, "clipboard", {value: {writeText}, configurable: true})
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```\nconst x = 42\n```"}})

        await wrapper.find(".copy").trigger("click")

        expect(writeText).toHaveBeenCalledWith("const x = 42")
    })

    test("renders a mermaid block as a diagram, not a code block", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```mermaid\ngraph TD\n  A --> B\n```"}})

        expect(wrapper.find(".diagram").text()).toContain("graph TD")
        expect(wrapper.find(".code-block").exists()).toBe(false)
    })

    test("highlights a code block once Shiki has loaded", async () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```javascript\nconst x = 1\n```"}})

        await vi.waitFor(
            () => expect(wrapper.find(".code-block code span[style]").exists()).toBe(true),
            {timeout: 5000, interval: 50},
        )
        expect(wrapper.find(".code-block code").text()).toBe("const x = 1")
    })

    test("highlights a code block that has no language", async () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```\nplain text\n```"}})

        await vi.waitFor(
            () => expect(wrapper.find(".code-block code span").exists()).toBe(true),
            {timeout: 5000, interval: 20},
        )
        expect(wrapper.find(".code-block code").text()).toBe("plain text")
    })

    test("highlights a language that is not pre-registered, from Shiki's on-demand bundle", async () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```elixir\ndefmodule Greeter do\nend\n```"}})

        await vi.waitFor(
            () => expect(wrapper.find(".code-block code span[style]").exists()).toBe(true),
            {timeout: 10000, interval: 50},
        )
        expect(wrapper.find(".code-block code").text()).toContain("defmodule")
    }, 15000)

    test("falls back to plain text for a language Shiki does not know", async () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "```notarealilanguage\nsome code\n```"}})
        await flushPromises()

        expect(wrapper.text()).toContain("some code")
    })

    test("keeps rendered content mounted when highlighting completes or the content grows", async () => {
        let mounts = 0
        const Probe = markRaw(defineComponent({name: "Probe", mounted: () => { mounts++ }, render: () => h("i")}))
        const content = "<Probe></Probe>\n\n```json\n{\"a\": 1}\n```"
        const wrapper = i18nMount(KsMarkdown, {props: {content, components: {Probe}}})

        await vi.waitFor(
            () => expect(wrapper.find(".code-block code span[style]").exists()).toBe(true),
            {timeout: 5000, interval: 20},
        )
        await wrapper.setProps({content: `${content}\n\nmore`})
        await flushPromises()

        expect(mounts).toBe(1)
    })

    test("loads the grammars of a multi-language document concurrently", async () => {
        const calls: string[] = []
        vi.mocked(loadLanguageOnDemand).mockImplementation(async (_highlighter, lang) => {
            calls.push(`enter:${lang}`)
            await new Promise((resolve) => setTimeout(resolve, 20))
            calls.push(`exit:${lang}`)
            return true
        })

        i18nMount(KsMarkdown, {props: {content: "```rust\nfn a() {}\n```\n\n```go\nfunc b() {}\n```"}})

        await vi.waitFor(() => expect(calls).toHaveLength(4), {timeout: 5000, interval: 10})
        expect(calls.slice(0, 2).sort()).toEqual(["enter:go", "enter:rust"])
    })

    test("a pending grammar load does not discard a highlight rendered while it waited", async () => {
        let releaseRust = () => {}
        let rustSettled = false
        const rustPending = new Promise<void>((resolve) => {
            releaseRust = resolve
        })
        vi.mocked(loadLanguageOnDemand).mockImplementation(async (_highlighter, lang) => {
            if (lang !== "rust") {
                return true
            }

            await rustPending
            rustSettled = true
            return false
        })

        const wrapper = i18nMount(KsMarkdown, {props: {content: "```rust\nfn a() {}\n```"}})
        await flushPromises()
        await wrapper.setProps({content: "```json\n{\"a\": 1}\n```"})
        await vi.waitFor(
            () => expect(wrapper.find(".code-block code span[style]").exists()).toBe(true),
            {timeout: 5000, interval: 20},
        )

        releaseRust()
        await vi.waitFor(() => expect(rustSettled).toBe(true), {timeout: 5000, interval: 10})
        await flushPromises()

        expect(wrapper.find(".code-block code span[style]").exists()).toBe(true)
    })

    test("strips <script> tags from raw HTML", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "<script>alert('xss')</script>"}})

        expect(wrapper.find("script").exists()).toBe(false)
    })

    test("strips event-handler attributes from raw HTML", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "<img src=\"x\" onerror=\"alert('xss')\">"}})

        expect(wrapper.html()).not.toContain("onerror")
    })

    test("injects raw HTML verbatim when xssProtection is disabled", () => {
        const wrapper = i18nMount(KsMarkdown, {
            props: {content: "<img src=\"x\" onerror=\"alert('xss')\">", xssProtection: false},
        })

        expect(wrapper.html()).toContain("onerror")
    })

    test("renders markdown nested inside inline HTML tags", () => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: "Press <kbd>Ctrl</kbd> or <mark>**this**</mark>"}})

        expect(wrapper.find("kbd").text()).toBe("Ctrl")
        expect(wrapper.find("mark strong").text()).toBe("this")
    })

    test("sanitizes an inline HTML tag before rendering its children", () => {
        const wrapper = i18nMount(KsMarkdown, {
            props: {content: "<a href=\"javascript:alert(1)\" onmouseover=\"alert(1)\">click</a>"},
        })
        const link = wrapper.find("a")

        expect(link.text()).toBe("click")
        expect(link.attributes("onmouseover")).toBeUndefined()
        expect(link.attributes("href") ?? "").not.toContain("javascript")
    })

    test("sanitizes the inner HTML of a custom component", () => {
        const ChildCard = markRaw({name: "ChildCard", template: "<div class=\"child-card\"><slot /></div>"})
        const wrapper = i18nMount(KsMarkdown, {
            props: {
                content: "<ChildCard><img src=x onerror=\"alert('xss')\"></ChildCard>",
                components: {ChildCard},
            },
        })

        expect(wrapper.find(".child-card").exists()).toBe(true)
        expect(wrapper.html()).not.toContain("onerror")
    })

    test("sanitizes the attributes passed to a custom component", () => {
        const Link = markRaw(defineComponent({name: "Link", setup: (_, {slots}) => () => h("a", slots.default?.())}))
        const wrapper = i18nMount(KsMarkdown, {
            props: {
                content: "<a href=\"javascript:alert(1)\" ONMOUSEOVER=\"alert(1)\" style=\"position:fixed\">x</a>",
                components: {a: Link},
            },
        })
        const attributes = wrapper.find("a").attributes()

        expect(attributes).not.toHaveProperty("href")
        expect(attributes).not.toHaveProperty("onmouseover")
        expect(attributes).not.toHaveProperty("style")
    })

    test.each([
        ["https://www.youtube.com/embed/abc", "https://www.youtube.com/embed/abc"],
        ["https://www.youtube-nocookie.com/embed/abc", "https://www.youtube-nocookie.com/embed/abc"],
        ["https://demo.arcade.software/abc?embed", "https://demo.arcade.software/abc?embed"],
        ["http://www.youtube.com/embed/abc", undefined],
        ["https://evil.example.com/phish", undefined],
    ])("embeds only HTTPS YouTube and Arcade iframes: %s", (url, src) => {
        const wrapper = i18nMount(KsMarkdown, {props: {content: `<iframe src="${url}"></iframe>`}})

        expect(wrapper.find("iframe").attributes("src")).toBe(src)
    })
})
