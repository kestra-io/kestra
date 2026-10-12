import {describe, it, expect, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {ref} from "vue"
import {useDocsLink} from "../../../../src/components/docs/useDocsLink"

const UTM = "?utm_source=app&utm_medium=referral&utm_campaign=embed-docs"
const PANEL = "docs/workflow-components/inputs"
const PAGE = "/main/docs/workflow-components/inputs"

function link(href: string, currentPath: string) {
    return useDocsLink(ref(href), ref(currentPath))
}

describe("useDocsLink", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    describe("kestra docs links stay in the integrated docs", () => {
        it("context panel keeps the docs/ prefix", () => {
            const {href, isRemote} = link("https://kestra.io/docs/workflow-components/tasks", PANEL)
            expect(isRemote.value).toBe(false)
            expect(href.value).toBe("docs/workflow-components/tasks")
        })

        it("full page resolves against the tenant docs root", () => {
            const {href, isRemote} = link("https://kestra.io/docs/workflow-components/tasks", PAGE)
            expect(isRemote.value).toBe(false)
            expect(href.value).toBe("/main/docs/workflow-components/tasks")
        })

        it("handles root-relative /docs links in both shapes", () => {
            expect(link("/docs/a/b", PANEL).href.value).toBe("docs/a/b")
            expect(link("/docs/a/b", PAGE).href.value).toBe("/main/docs/a/b")
        })

        it("drops trailing slash, query and anchor", () => {
            const {href} = link("https://kestra.io/docs/workflow-components/inputs/?x=1#section", PANEL)
            expect(href.value).toBe("docs/workflow-components/inputs")
        })

        it("docs root resolves to the docs root, not an empty path", () => {
            expect(link("https://kestra.io/docs", PANEL).href.value).toBe("docs")
            expect(link("https://kestra.io/docs", PAGE).href.value).toBe("/main/docs")
        })
    })

    describe("other links stay remote", () => {
        it("keeps non-docs kestra.io links remote", () => {
            const {href, isRemote} = link("/blogs/some-post", PANEL)
            expect(isRemote.value).toBe(true)
            expect(href.value).toBe("https://kestra.io/blogs/some-post" + UTM)
        })

        it("does not treat lookalike paths or hosts as docs", () => {
            expect(link("/docs-old/x", PANEL).isRemote.value).toBe(true)
            expect(link("https://docs.kestra.io/x", PANEL).isRemote.value).toBe(true)
        })

        it("keeps external sites untouched", () => {
            const {href, isRemote} = link("https://example.com/docs/x", PANEL)
            expect(isRemote.value).toBe(true)
            expect(href.value).toBe("https://example.com/docs/x")
        })
    })
})
