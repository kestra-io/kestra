import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import ErrorAlert from "./ErrorAlert.vue"
import {useExecutionsStore, type Execution} from "../../../../../stores/executions"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import {routedPinia} from "../../../../../../tests/unit/memoryRouter"

const messages = {
    execution_failed: "Execution failed!",
    "last error was": "Last error was",
    errorLogs: "Error logs",
    expand: "Expand",
    collapse: "Collapse",
}

const execution = {id: "e1", namespace: "ns", flowId: "flow", kind: "NORMAL", state: {current: "FAILED"}} as unknown as Execution

const errorLogs = ["EMEA", "AMER", "LATAM", "ANZ", "JP", "APAC"].map((region, index) => ({
    level: "ERROR",
    taskId: "reconcile",
    timestamp: `2026-10-14T11:55:49.${700 + index}Z`,
    message: `Ledger ${region} is off`,
}))

const mountAlert = () => i18nMount(ErrorAlert, {
    messages,
    props: {execution},
    global: {
        stubs: {
            RouterLink: {template: "<a><slot /></a>"},
            LogLine: {props: ["log"], template: "<div class=\"log-line\">{{ log.message }}</div>"},
        },
    },
})

describe("ErrorAlert", () => {
    beforeEach(() => {
        setActivePinia(routedPinia())
        vi.spyOn(useExecutionsStore(), "loadLogs").mockResolvedValue(errorLogs as never)
    })

    afterAll(() => localStorage.clear())

    it("ends the expanded view with the error the collapsed preview quotes", async () => {
        // Given
        const wrapper = mountAlert()
        await flushPromises()
        expect(wrapper.find(".error-preview").text()).toContain("Ledger APAC is off")

        // When
        await wrapper.find("[data-test=\"error-alert-toggle\"]").trigger("click")

        // Then
        const lines = wrapper.findAll(".log-line").map(line => line.text())
        expect(lines).toEqual(["Ledger LATAM is off", "Ledger ANZ is off", "Ledger JP is off", "Ledger APAC is off"])
    })

    it("names the toggle and reports whether the logs are shown", async () => {
        // Given
        const wrapper = mountAlert()
        await flushPromises()
        const toggle = () => wrapper.find("[data-test=\"error-alert-toggle\"]")
        expect(toggle().attributes("aria-label")).toBe("Expand")
        expect(toggle().attributes("aria-expanded")).toBe("false")

        // When
        await toggle().trigger("click")

        // Then
        expect(toggle().attributes("aria-label")).toBe("Collapse")
        expect(toggle().attributes("aria-expanded")).toBe("true")
    })
})
