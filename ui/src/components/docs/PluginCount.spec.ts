import { describe, expect, it, vi } from "vitest"
import { mount } from "@vue/test-utils"
import { nextTick, ref } from "vue"
import PluginCount from "./PluginCount.vue"

const totalPlugins = ref(5)

vi.mock("../../composables/usePluginsCount", () => ({
  usePluginsCount: () => ({
    totalPlugins,
  }),
}))

describe("PluginCount", () => {
  it("renders the total plugin count followed by a plus sign", () => {
    const wrapper = mount(PluginCount)

    expect(wrapper.text()).toBe("5+")
  })

  it("updates when the total plugin count changes", async () => {
    const wrapper = mount(PluginCount)

    totalPlugins.value = 10
    await nextTick()

    expect(wrapper.text()).toBe("10+")
  })

  it("does not inherit attributes onto the root element", () => {
    const wrapper = mount(PluginCount, {
      attrs: { class: "parent-class" },
    })

    expect(wrapper.classes()).not.toContain("parent-class")
  })
})