import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import McpIcon from "./McpIcon.vue"

describe("McpIcon", () => {
    describe("defaults", () => {
        test("uses currentColor, 24 and 12 as the prop defaults", () => {
            const wrapper = mount(McpIcon)

            expect(wrapper.props("fillColor")).toBe("currentColor")
            expect(wrapper.props("size")).toBe(24)
            expect(wrapper.props("strokeWidth")).toBe(12)
        })

        test("renders the defaults on the svg and its paths", () => {
            const wrapper = mount(McpIcon)
            const svg = wrapper.find("svg")

            expect(svg.attributes("stroke")).toBe("currentColor")
            expect(svg.attributes("width")).toBe("24")
            expect(svg.attributes("height")).toBe("24")
            wrapper.findAll("path").forEach(path => {
                expect(path.attributes("stroke-width")).toBe("12")
            })
        })
    })

    describe("size", () => {
        test("sets both the svg width and height from size", () => {
            const wrapper = mount(McpIcon, {props: {size: 48}})
            const svg = wrapper.find("svg")

            expect(svg.attributes("width")).toBe("48")
            expect(svg.attributes("height")).toBe("48")
        })
    })

    describe("stroke", () => {
        test("applies fillColor as the svg stroke", () => {
            const wrapper = mount(McpIcon, {props: {fillColor: "#ff0000"}})

            expect(wrapper.find("svg").attributes("stroke")).toBe("#ff0000")
        })

        test("applies strokeWidth to each path", () => {
            const wrapper = mount(McpIcon, {props: {strokeWidth: 20}})
            const paths = wrapper.findAll("path")

            expect(paths).toHaveLength(3)
            paths.forEach(path => {
                expect(path.attributes("stroke-width")).toBe("20")
            })
        })
    })

    describe("title and accessibility", () => {
        test("does not render a title element when no title is passed", () => {
            const wrapper = mount(McpIcon)

            expect(wrapper.find("title").exists()).toBe(false)
        })

        test("renders the title element when a title is passed", () => {
            const wrapper = mount(McpIcon, {props: {title: "MCP"}})
            const title = wrapper.find("title")

            expect(title.exists()).toBe(true)
            expect(title.text()).toBe("MCP")
        })

        test("is hidden from assistive technology without a title", () => {
            const wrapper = mount(McpIcon)

            expect(wrapper.attributes("aria-hidden")).toBe("true")
            expect(wrapper.attributes("aria-label")).toBeUndefined()
        })

        test("exposes a labelled image with a title", () => {
            const wrapper = mount(McpIcon, {props: {title: "MCP"}})

            expect(wrapper.attributes("aria-hidden")).toBeUndefined()
            expect(wrapper.attributes("aria-label")).toBe("MCP")
            expect(wrapper.attributes("role")).toBe("img")
        })
    })

    describe("events", () => {
        test("emits click with the event when clicked", async () => {
            const wrapper = mount(McpIcon)

            await wrapper.trigger("click")

            const clicks = wrapper.emitted("click")
            expect(clicks).toHaveLength(1)
            expect(clicks![0][0]).toBeInstanceOf(MouseEvent)
        })
    })
})
