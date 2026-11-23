import {afterEach, describe, expect, it} from "vitest"
import LogLine from "./LogLine.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const mountLine = () => i18nMount(LogLine, {
    props: {log: {level: "INFO", message: "hello", timestamp: "2026-10-14T11:55:49.884Z"}},
    global: {stubs: {CopyToClipboard: true, LogValueActions: true}},
})

describe("LogLine", () => {
    afterEach(() => {
        localStorage.clear()
    })

    it("renders the full timestamp with a space between the date and the time", () => {
        // Given / When
        const wrapper = mountLine()

        // Then
        expect(wrapper.find(".log-time").text()).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$/)
    })

    it("renders a single time element per line", () => {
        // Given / When
        const wrapper = mountLine()

        // Then
        expect(wrapper.findAll("time")).toHaveLength(1)
    })
})
