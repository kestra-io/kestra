import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import AiMenuIcon from "./AiMenuIcon.vue"

describe("AiMenuIcon", () => {
    test("size is applied to both width and height, and defaults to 24", () => {
        const defaultWrapper = mount(AiMenuIcon)
        const defaultSvg = defaultWrapper.find("svg")
        expect(defaultSvg.attributes("width")).toBe("24")
        expect(defaultSvg.attributes("height")).toBe("24")

        const customWrapper = mount(AiMenuIcon, {
            props: {size: 32},
        })
        const customSvg = customWrapper.find("svg")
        expect(customSvg.attributes("width")).toBe("32")
        expect(customSvg.attributes("height")).toBe("32")
    })

    test("fillColor is applied to the path, and defaults to currentColor", () => {
        const defaultWrapper = mount(AiMenuIcon)
        expect(defaultWrapper.find("path").attributes("fill")).toBe("currentColor")

        const customWrapper = mount(AiMenuIcon, {
            props: {fillColor: "#ff0000"},
        })
        expect(customWrapper.find("path").attributes("fill")).toBe("#ff0000")
    })

    test("the wrapper carries role='img' and aria-hidden='true'", () => {
        const wrapper = mount(AiMenuIcon)
        expect(wrapper.attributes("role")).toBe("img")
        expect(wrapper.attributes("aria-hidden")).toBe("true")
    })

    test("inherited attributes land on the wrapper element", () => {
        const wrapper = mount(AiMenuIcon, {
            attrs: {
                "data-test": "custom-ai-icon",
                title: "AI Menu",
            },
        })
        expect(wrapper.attributes("data-test")).toBe("custom-ai-icon")
        expect(wrapper.attributes("title")).toBe("AI Menu")
    })

    test("the viewBox stays '0 0 20 20' whatever the size", () => {
        const defaultWrapper = mount(AiMenuIcon)
        expect(defaultWrapper.find("svg").attributes("viewBox")).toBe("0 0 20 20")

        const resizedWrapper = mount(AiMenuIcon, {
            props: {size: 48},
        })
        expect(resizedWrapper.find("svg").attributes("viewBox")).toBe("0 0 20 20")
    })
})
