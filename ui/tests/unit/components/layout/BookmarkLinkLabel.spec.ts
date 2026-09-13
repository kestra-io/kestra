import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h} from "vue"

vi.mock("@kestra-io/design-system", () => ({
    KsInput: defineComponent({name: "KsInput", props: {modelValue: {type: String, default: ""}}, setup: () => () => h("input")}),
    KsMessageBox: {confirm: vi.fn(() => Promise.resolve())},
}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (k: string) => k})}))
for (const icon of ["DeleteOutline", "PencilOutline", "CheckCircle"]) {
    vi.doMock(`vue-material-design-icons/${icon}.vue`, () => ({
        default: defineComponent({name: icon, setup: () => () => h("span")}),
    }))
}

const {createPinia, setActivePinia} = await import("pinia")
const BookmarkLink = (await import("../../../../src/components/layout/BookmarkLink.vue")).default

const RouterLinkStub = defineComponent({
    name: "RouterLink",
    props: {to: {type: [String, Object], default: undefined}},
    setup: (_p, {slots, attrs}) => () => h("a", attrs, slots.default?.()),
})

let wrapper: ReturnType<typeof mount> | undefined

// The store persists through `useStorage`, so the key outlives the test unless it is cleared.
beforeEach(() => localStorage.clear())
afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
})

const mountLink = (title: string) => {
    setActivePinia(createPinia())
    wrapper = mount(BookmarkLink, {
        props: {href: "/flows", title},
        global: {stubs: {RouterLink: RouterLinkStub}, mocks: {$t: (k: string) => k}},
    })
    return wrapper
}

describe("BookmarkLink label", () => {
    // The rendered label used to come from a ref seeded once at setup, so a label corrected in
    // the store never reached the sidebar without a remount — and the list keys on the path.
    it("should render an updated title without being remounted", async () => {
        const link = mountLink("Flows: Ausfuehrungen")
        expect(link.find(".bookmark-title").text()).toBe("Flows: Ausfuehrungen")

        await link.setProps({title: "Flows: Executions"})

        expect(link.find(".bookmark-title").text()).toBe("Flows: Executions")
    })

    it("should carry the current title in the link tooltip", async () => {
        const link = mountLink("Flows")

        await link.setProps({title: "Ablaeufe"})

        expect(link.find(".bookmark-anchor").attributes("title")).toBe("Ablaeufe")
    })
})
