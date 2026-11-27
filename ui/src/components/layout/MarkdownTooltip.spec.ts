import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import MarkdownTooltip from "./MarkdownTooltip.vue"

vi.mock("@kestra-io/design-system", () => ({
    KsMarkdown: defineComponent({
        name: "KsMarkdown",
        props: {content: String},
        template: "<div data-test=\"markdown\">{{ content }}</div>",
    }),
}))

const KsDrawer = defineComponent({
    name: "KsDrawer",
    props: {modelValue: Boolean, title: String},
    template: "<div data-test=\"drawer\"><slot /></div>",
})

const mountTooltip = (props: Record<string, string> = {}) =>
    mount(MarkdownTooltip, {props: {id: "flow", ...props}, global: {components: {KsDrawer}}})

describe("MarkdownTooltip", () => {
    it("renders nothing when the description is empty", () => {
        const wrapper = mountTooltip({description: ""})

        expect(wrapper.html()).toBe("<!--v-if-->")
    })

    it("renders nothing when the description is left out", () => {
        const wrapper = mountTooltip()

        expect(wrapper.html()).toBe("<!--v-if-->")
    })

    it("renders the help icon when a description is given", () => {
        const wrapper = mountTooltip({description: "Some **help**"})

        expect(wrapper.find("a").exists()).toBe(true)
        expect(wrapper.find("a .material-design-icon").exists()).toBe(true)
    })

    it("builds the icon id from the id prop", () => {
        const wrapper = mountTooltip({id: "namespace", description: "Some help"})

        expect(wrapper.find("#tooltip-desc-namespace").exists()).toBe(true)
    })

    it("keeps the drawer closed until the icon is clicked", async () => {
        const wrapper = mountTooltip({description: "Some help"})

        expect(wrapper.findComponent({name: "KsDrawer"}).exists()).toBe(false)

        await wrapper.find("a").trigger("click")

        expect(wrapper.findComponent({name: "KsDrawer"}).exists()).toBe(true)
    })

    it("gives the drawer the title and the markdown the description", async () => {
        const wrapper = mountTooltip({title: "Flow help", description: "Some **help**"})

        await wrapper.find("a").trigger("click")

        expect(wrapper.findComponent({name: "KsDrawer"}).props("title")).toBe("Flow help")
        expect(wrapper.findComponent({name: "KsMarkdown"}).props("content")).toBe("Some **help**")
    })
})
