import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import ThemeWindow from "./ThemeWindow.vue"

describe("ThemeWindow", () => {
    test("the svg is rendered with its viewBox", () => {
        const wrapper = mount(ThemeWindow)
        const svg = wrapper.find("svg.theme-window")

        expect(svg.exists()).toBe(true)
        expect(svg.attributes("viewBox")).toBe("0 0 124 85")
    })

    test("two instances get different clip path ids", () => {
        const first = mount(ThemeWindow)
        const second = mount(ThemeWindow)

        const firstClipId = first.find("clipPath").attributes("id")
        const secondClipId = second.find("clipPath").attributes("id")

        expect(firstClipId).toBeTruthy()
        expect(secondClipId).toBeTruthy()
        expect(firstClipId).not.toBe(secondClipId)
    })

    test("the group's clip-path references that instance's own id", () => {
        const wrapper = mount(ThemeWindow)
        const clipId = wrapper.find("clipPath").attributes("id")
        const group = wrapper.find("g")

        expect(clipId).toBeTruthy()
        expect(group.attributes("clip-path")).toBe(`url(#${clipId})`)
    })

    test("the sidebar, main and frame parts are present", () => {
        const wrapper = mount(ThemeWindow)

        expect(wrapper.find(".tw-sidebar").exists()).toBe(true)
        expect(wrapper.find(".tw-main").exists()).toBe(true)
        expect(wrapper.find(".tw-frame").exists()).toBe(true)
    })
})

