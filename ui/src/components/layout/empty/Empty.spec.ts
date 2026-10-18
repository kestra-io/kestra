import {describe, it, expect} from "vitest"
import {KsButton, KsEmptyState} from "@kestra-io/design-system"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import Empty from "./Empty.vue"
import generic from "../../../assets/empty_visuals/generic.svg"
import {images} from "./images"
import {links} from "./links"

describe("Empty.vue", () => {
    it("an explicit title and description win over the translation lookup", () => {
        const messages = {
            empty: {
                blueprints: {
                    title: "Translated Blueprints",
                    content: "Translated blueprints content",
                },
            },
        }

        const wrapper = i18nMount(Empty, {
            props: {
                type: "blueprints",
                title: "Custom Title",
                description: "Custom Description",
            },
            messages,
        })

        const emptyState = wrapper.findComponent(KsEmptyState)
        expect(emptyState.props("title")).toBe("Custom Title")
        expect(emptyState.props("description")).toBe("Custom Description")
    })

    it("falls back to empty.<type>.title and empty.<type>.content when those keys exist", () => {
        const messages = {
            empty: {
                blueprints: {
                    title: "Translated Title",
                    content: "Translated Content",
                },
            },
        }

        const wrapper = i18nMount(Empty, {
            props: {
                type: "blueprints",
            },
            messages,
        })

        const emptyState = wrapper.findComponent(KsEmptyState)
        expect(emptyState.props("title")).toBe("Translated Title")
        expect(emptyState.props("description")).toBe("Translated Content")
    })

    it("a type with no translation keys renders without a title or description, not the raw key", () => {
        const wrapper = i18nMount(Empty, {
            props: {
                type: "untranslated_type",
            },
        })

        const emptyState = wrapper.findComponent(KsEmptyState)
        expect(emptyState.props("title")).toBeUndefined()
        expect(emptyState.props("description")).toBeUndefined()
    })

    it("learnMore falls back to the docs link for that type or uses an explicit prop", () => {
        const defaultDocWrapper = i18nMount(Empty, {
            props: {
                type: "namespaceFiles",
            },
        })
        expect(defaultDocWrapper.findComponent(KsEmptyState).props("learnMore")).toBe(links.namespaceFiles)

        const explicitWrapper = i18nMount(Empty, {
            props: {
                type: "namespaceFiles",
                learnMore: "https://kestra.io/custom-learn-more",
            },
        })
        expect(explicitWrapper.findComponent(KsEmptyState).props("learnMore")).toBe("https://kestra.io/custom-learn-more")
    })

    it("a type with no docs link renders no learn-more", () => {
        const wrapper = i18nMount(Empty, {
            props: {
                type: "type_without_docs_link",
            },
        })

        expect(wrapper.findComponent(KsEmptyState).props("learnMore")).toBeUndefined()
    })

    it("picks image by type, falling back to the generic visual", () => {
        const blueprintWrapper = i18nMount(Empty, {
            props: {
                type: "blueprints",
            },
        })
        expect(blueprintWrapper.findComponent(KsEmptyState).props("image")).toBe(images.blueprints)

        const fallbackWrapper = i18nMount(Empty, {
            props: {
                type: "type_without_dedicated_artwork",
            },
        })
        expect(fallbackWrapper.findComponent(KsEmptyState).props("image")).toBe(generic)
    })

    it("demoCta controls the contact-sales link, whose URL carries the type", () => {
        const noCtaWrapper = i18nMount(Empty, {
            props: {
                type: "quotas",
                demoCta: false,
            },
        })
        const hasDemoButton = noCtaWrapper
            .findAllComponents(KsButton)
            .some((btn) => btn.attributes("href")?.includes("kestra.io/demo"))
        expect(hasDemoButton).toBe(false)

        const ctaWrapper = i18nMount(Empty, {
            props: {
                type: "quotas",
                demoCta: true,
            },
        })
        const demoBtn = ctaWrapper
            .findAllComponents(KsButton)
            .find((btn) => btn.attributes("href")?.includes("kestra.io/demo"))
        expect(demoBtn).toBeDefined()
        expect(demoBtn?.attributes("href")).toBe(
            "https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-quotas",
        )
    })
})
