import {afterEach, beforeAll, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KestraDesignSystem from "../../../src/index"
import KsTooltip from "../../../src/components/Feedback/KsTooltip.vue"

const ElTooltipStub = {
    name: "ElTooltip",
    template: "<div><slot /></div>",
    props: ["effect", "content", "placement", "trigger", "enterable", "rawContent", "disabled", "autoClose"],
}

const globalConfig = {plugins: [KestraDesignSystem]}
const globalConfigWithStub = {
    plugins: [KestraDesignSystem],
    stubs: {ElTooltip: ElTooltipStub},
}

beforeAll(() => {
    vi.stubGlobal(
        "MutationObserver",
        class {
            observe() {}
            disconnect() {}
        },
    )
})

describe("KsTooltip", () => {
    test("renders tooltip trigger element", () => {
        const wrapper = mount(KsTooltip, {
            props: {content: "Test tooltip"},
            slots: {default: "<button>Hover me</button>"},
            global: globalConfig,
        })
        expect(wrapper.element.childElementCount > 0 || wrapper.html().includes("button")).toBe(true)
    })

    test("disabled prop is accepted", () => {
        const wrapper = mount(KsTooltip, {
            props: {content: "Test", disabled: true},
            slots: {default: "<button>Hover me</button>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })
})

describe("KsTooltip — default effect", () => {
    afterEach(() => {
        document.documentElement.classList.remove("dark")
    })

    test("defaults to dark effect on light theme", () => {
        const wrapper = mount(KsTooltip, {global: globalConfigWithStub})

        expect(wrapper.findComponent(ElTooltipStub).props("effect")).toBe("dark")
    })

    test("defaults to light effect on dark theme", async () => {
        document.documentElement.classList.add("dark")
        const wrapper = mount(KsTooltip, {global: globalConfigWithStub})
        await nextTick()

        expect(wrapper.findComponent(ElTooltipStub).props("effect")).toBe("light")
    })

    test("explicit effect prop overrides theme default", async () => {
        document.documentElement.classList.add("dark")
        const wrapper = mount(KsTooltip, {
            props: {effect: "dark"},
            global: globalConfigWithStub,
        })
        await nextTick()

        expect(wrapper.findComponent(ElTooltipStub).props("effect")).toBe("dark")
    })
})
