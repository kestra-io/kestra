
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest"
import { mount } from "@vue/test-utils"
import { defineComponent, h } from "vue"

vi.mock("@kestra-io/design-system", () => ({
    KsMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({ t: (key: string) => key }),
}))

const { createPinia, setActivePinia } = await import("pinia")

const BookmarkLinkList = (await import("./BookmarkLinkList.vue")).default

const KsInput = defineComponent({
    name: "KsInput",
    props: {
        modelValue: { type: String, default: "" },
    },
    setup: () => () => h("input"),
})

const RouterLinkStub = defineComponent({
    name: "RouterLink",
    inheritAttrs: false,
    props: {
        to: { type: [String, Object], default: undefined },
    },
    setup: (props, { slots, attrs }) => () =>
        h(
            "a",
            {
                ...attrs,
                href: typeof props.to === "string" ? props.to : undefined,
            },
            slots.default?.(),
        ),
})

let wrapper: ReturnType<typeof mount> | undefined

beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
})

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
})

const mountList = (pages: { label?: string; path: string }[]) => {
    wrapper = mount(BookmarkLinkList, {
        props: { pages },
        global: {
            stubs: { RouterLink: RouterLinkStub, KsInput },
            mocks: { $t: (key: string) => key },
        },
    })

    return wrapper
}

describe("BookmarkLinkList", () => {
    test("renders one link for each page in the correct order", () => {
        const pages = [
            { label: "Overview", path: "/overview" },
            { label: "Settings", path: "/settings" },
        ]

        const wrapper = mountList(pages)
        const links = wrapper.findAll(".bookmark-anchor")

        expect(links).toHaveLength(2)
        expect(links[0].attributes("href")).toBe("/overview")
        expect(links[1].attributes("href")).toBe("/settings")
    })

    test("uses the label as the title when a label exists", () => {
        const wrapper = mountList([
            { label: "My Dashboard", path: "/dashboard" },
        ])

        expect(wrapper.find(".bookmark-anchor").attributes("title"))
            .toBe("My Dashboard")
    })

    test("falls back to the path when the label is missing", () => {
        const wrapper = mountList([{ path: "/reports" }])

        expect(wrapper.find(".bookmark-anchor").attributes("title"))
            .toBe("/reports")
    })
})
