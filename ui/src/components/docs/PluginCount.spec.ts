import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick, ref} from "vue"
import PluginCount from "./PluginCount.vue"

const count = ref(1800)

vi.mock("../../composables/usePluginsCount", () => ({
    usePluginsCount: () => ({totalPlugins: count}),
}))

describe("PluginCount", () => {
    it("shouldRenderTotalFollowedByPlusSign", () => {
        count.value = 1800
        const wrapper = mount(PluginCount)

        expect(wrapper.text()).toContain("1800")
        expect(wrapper.text()).toContain("+")
    })

    it("shouldReRenderWhenTotalChanges", async () => {
        count.value = 1800
        const wrapper = mount(PluginCount)

        count.value = 2000
        await nextTick()

        expect(wrapper.text()).toContain("2000")
    })

    it("shouldNotForwardClassToRootElementDueToInheritAttrsFalse", () => {
        count.value = 1800
        const wrapper = mount(PluginCount, {attrs: {class: "custom-class"}})

        expect(wrapper.element.classList.contains("custom-class")).toBe(false)
    })
})
