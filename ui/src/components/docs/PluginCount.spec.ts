import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import PluginCount from "./PluginCount.vue"
import {usePluginsCount} from "../../composables/usePluginsCount"

vi.mock("../../composables/usePluginsCount", async () => {
    const {ref} = await import("vue")
    const totalPlugins = ref(1800)
    return {usePluginsCount: () => ({totalPlugins})}
})

describe("PluginCount", () => {
    it("renders the total followed by a plus sign", () => {
        const wrapper = mount(PluginCount)

        expect(wrapper.text()).toContain("1800+")
    })

    it("re-renders when the total changes", async () => {
        const wrapper = mount(PluginCount)
        const {totalPlugins} = usePluginsCount()

        totalPlugins.value = 1900
        await nextTick()

        expect(wrapper.text()).toContain("1900+")
    })

    it("does not let inherited attributes land on the root element", () => {
        const wrapper = mount(PluginCount, {attrs: {class: "from-parent"}})

        expect(wrapper.attributes("class")).toBeUndefined()
    })
})
