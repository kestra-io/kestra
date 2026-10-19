import {describe, it, expect, afterAll} from "vitest"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import LogLine from "./LogLine.vue"
import type {Log} from "../../stores/logs"

afterAll(() => {
    localStorage.clear()
})

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div />"}}],
})

function mountLogLine(props: {
    log: Partial<Log>
    cursor?: boolean
    dimmed?: boolean
    clickableLevel?: boolean
    filter?: string
}) {
    return i18nMount(LogLine, {
        props,
        global: {
            plugins: [router, KestraDesignSystem],
        },
    })
}

describe("LogLine", () => {
    it("renders log level label, message and level classes", () => {
        const wrapper = mountLogLine({
            log: {
                level: "INFO",
                message: "Hello world log message",
                timestamp: "2026-10-18T10:00:00Z",
            },
        })

        const line = wrapper.find("div.line")
        expect(line.exists()).toBe(true)
        expect(line.classes()).toContain("log-row-info")
        expect(wrapper.find(".log-level").text()).toBe("Info")
        expect(wrapper.find(".log-message").text()).toContain("Hello world log message")
    })

    it("applies log-row-error class without inline background so CSS selection works", () => {
        const wrapper = mountLogLine({
            log: {
                level: "ERROR",
                message: "Failed with error",
            },
        })

        const line = wrapper.find("div.line")
        expect(line.classes()).toContain("log-row-error")
        // rowStyle must NOT set inline background, ensuring CSS .selected and :hover take effect
        const style = line.attributes("style") ?? ""
        expect(style).not.toContain("background")
        expect(style).toContain("border-left-color: var(--ks-log-border-error)")
    })

    it("applies log-row-warn class without inline background so CSS selection works", () => {
        const wrapper = mountLogLine({
            log: {
                level: "WARN",
                message: "Warning message",
            },
        })

        const line = wrapper.find("div.line")
        expect(line.classes()).toContain("log-row-warn")
        const style = line.attributes("style") ?? ""
        expect(style).not.toContain("background")
        expect(style).toContain("border-left-color: var(--ks-log-border-warn)")
    })

    it("renders navigation cursor icon and .selected class when cursor prop is true", () => {
        const wrapper = mountLogLine({
            log: {
                level: "ERROR",
                message: "Focused error line",
            },
            cursor: true,
        })

        const line = wrapper.find("div.line")
        expect(line.classes()).toContain("selected")

        const container = wrapper.find(".log-level-container")
        expect(container.exists()).toBe(true)

        const icon = container.find(".icon_container")
        expect(icon.exists()).toBe(true)
    })

    it("omits cursor icon and .selected class when cursor prop is false or undefined", () => {
        const wrapper = mountLogLine({
            log: {
                level: "ERROR",
                message: "Unfocused error line",
            },
            cursor: false,
        })

        const line = wrapper.find("div.line")
        expect(line.classes()).not.toContain("selected")
        expect(wrapper.find(".icon_container").exists()).toBe(false)
    })

    it("applies dimmed class when dimmed is true", () => {
        const wrapper = mountLogLine({
            log: {
                level: "INFO",
                message: "Dimmed log line",
            },
            dimmed: true,
        })

        expect(wrapper.find("div.line").classes()).toContain("dimmed")
    })
})
