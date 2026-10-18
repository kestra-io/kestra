import {describe, it, expect, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import ContextChildCard from "./ContextChildCard.vue"

vi.mock("../../stores/doc", () => ({
    useDocStore: () => ({
        docPath: "docs",
        children: vi.fn().mockResolvedValue({
            "docs/quickstart": {
                title: "Quickstart Guide",
                description: "Learn how to build your first flow with [Kestra](https://kestra.io)",
            },
        }),
    }),
}))

describe("ContextChildCard.vue", () => {
    it("renders item content without Bootstrap utility classes", async () => {
        setActivePinia(createPinia())
        const wrapper = mount(ContextChildCard, {
            props: {
                pageUrl: "docs",
            },
            global: {
                stubs: {
                    ChevronRight: true,
                    ContextDocsLink: {
                        props: ["href"],
                        template: "<a><slot /></a>",
                    },
                },
            },
        })

        await flushPromises()

        const card = wrapper.find(".card")
        expect(card.exists()).toBe(true)
        expect(card.classes()).not.toContain("h-100")

        const cardBody = wrapper.find(".card-body")
        expect(cardBody.exists()).toBe(true)
        expect(cardBody.classes()).not.toContain("d-flex")
        expect(cardBody.classes()).not.toContain("align-items-center")

        const cardContent = wrapper.find(".card-content")
        expect(cardContent.exists()).toBe(true)
        expect(cardContent.classes()).not.toContain("overflow-hidden")

        const cardText = wrapper.find(".card-text")
        expect(cardText.exists()).toBe(true)
        expect(cardText.classes()).not.toContain("mb-0")
        expect(cardText.text()).toContain("Learn how to build your first flow with Kestra")
    })
})
