import {describe, expect, test} from "vitest"
import {defineComponent, h} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import Empty from "./Empty.vue"
import generic from "../../../assets/empty_visuals/generic.svg"
import {images} from "./images"
import {demoLink, links} from "./links"

const KsRowStub = defineComponent({
    name: "KsRow",
    inheritAttrs: false,
    setup(_, {attrs, slots}) {
        return () => h("section", attrs, slots.default?.())
    },
})

const KsColStub = defineComponent({
    name: "KsCol",
    inheritAttrs: false,
    setup(_, {slots}) {
        return () => h("div", slots.default?.())
    },
})

const KsEmptyStateStub = defineComponent({
    name: "KsEmptyState",
    props: {
        title: String,
        description: String,
        image: String,
        learnMore: String,
    },
    setup(props, {slots}) {
        return () =>
            h("div", {"data-test": "empty-state"}, [
                props.title && h("h2", props.title),
                props.description && h("p", props.description),
                props.learnMore && h("a", {href: props.learnMore}, "Learn more"),
                slots.description?.(),
                slots.action?.(),
            ])
    },
})

const KsButtonStub = defineComponent({
    name: "KsButton",
    props: {
        href: String,
        target: String,
        tag: String,
        type: String,
    },
    setup(props, {slots}) {
        return () =>
            h(
                props.tag ?? "button",
                {
                    href: props.href,
                    target: props.target,
                },
                slots.default?.(),
            )
    },
})

const global = {
    stubs: {
        KsButton: KsButtonStub,
        KsCol: KsColStub,
        KsEmptyState: KsEmptyStateStub,
        KsRow: KsRowStub,
    },
}

describe("Empty", () => {
    test("uses explicit title and description props over translations", () => {
        const wrapper = i18nMount(Empty, {
            global,
            messages: {
                empty: {
                    namespaceFiles: {
                        title: "Translated title",
                        content: "Translated description",
                    },
                },
            },
            props: {
                type: "namespaceFiles",
                title: "Provided title",
                description: "Provided description",
            },
        })

        const emptyState = wrapper.findComponent(KsEmptyStateStub)
        expect(emptyState.props("title")).toBe("Provided title")
        expect(emptyState.props("description")).toBe("Provided description")
    })

    test("falls back to type translations when title and description are not provided", () => {
        const wrapper = i18nMount(Empty, {
            global,
            messages: {
                empty: {
                    namespaceFiles: {
                        title: "Translated title",
                        content: "Translated description",
                    },
                },
            },
            props: {type: "namespaceFiles"},
        })

        const emptyState = wrapper.findComponent(KsEmptyStateStub)
        expect(emptyState.props("title")).toBe("Translated title")
        expect(emptyState.props("description")).toBe("Translated description")
    })

    test("omits title and description when translations are missing", () => {
        const wrapper = i18nMount(Empty, {
            global,
            props: {type: "unknown"},
        })

        const emptyState = wrapper.findComponent(KsEmptyStateStub)
        expect(emptyState.props("title")).toBeUndefined()
        expect(emptyState.props("description")).toBeUndefined()
        expect(wrapper.text()).not.toContain("empty.unknown.title")
        expect(wrapper.text()).not.toContain("empty.unknown.content")
    })

    test("uses the documentation link associated with the type by default", () => {
        const wrapper = i18nMount(Empty, {
            global,
            props: {type: "namespaceFiles"},
        })

        expect(wrapper.findComponent(KsEmptyStateStub).props("learnMore")).toBe(links.namespaceFiles)
    })

    test("does not render a learn-more link for types without documentation", () => {
        const wrapper = i18nMount(Empty, {
            global,
            props: {type: "unknown"},
        })

        expect(wrapper.find("a").exists()).toBe(false)
    })

    test.each([
        ["namespaceFiles", images.namespaceFiles],
        ["unknown", generic],
    ])("selects the expected image for type %s", (type, image) => {
        const wrapper = i18nMount(Empty, {
            global,
            props: {type},
        })

        expect(wrapper.findComponent(KsEmptyStateStub).props("image")).toBe(image)
    })

    test("renders the contact-sales link only when demoCta is enabled", () => {
        const withoutCta = i18nMount(Empty, {
            global,
            props: {type: "namespaceFiles"},
        })
        expect(withoutCta.find(`a[href="${demoLink("namespaceFiles")}"]`).exists()).toBe(false)

        const withCta = i18nMount(Empty, {
            global,
            messages: {
                demos: {contact_sales: "Contact sales"},
            },
            props: {type: "namespaceFiles", demoCta: true},
        })
        const contactSales = withCta.find(`a[href="${demoLink("namespaceFiles")}"]`)
        expect(contactSales.exists()).toBe(true)
        expect(contactSales.attributes("target")).toBe("_blank")
        expect(contactSales.text()).toBe("Contact sales")
    })
})
