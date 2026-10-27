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

// Newest first, as the server answers the paginated `sort=timestamp:desc` request.
const errorLogs = ["APAC", "JP", "ANZ", "LATAM"].map((region, index) => ({
    level: "ERROR",
    taskId: "reconcile",
    timestamp: `2026-10-14T11:55:49.${705 - index}Z`,
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

    it("requests only the 4 newest error logs from the server", async () => {
        // Given
        const loadLogs = vi.spyOn(useExecutionsStore(), "loadLogs").mockResolvedValue(errorLogs as never)

        // When
        mountAlert()
        await flushPromises()

        // Then
        expect(loadLogs).toHaveBeenCalledWith(expect.objectContaining({
            store: false,
            executionId: "e1",
            size: 4,
            sort: ["timestamp:desc"],
        }))
    })

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

    it.each([
        ["NORMAL", [{field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"}]],
        ["PLAYGROUND", [
            {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
            {field: "kind", operation: "IN", value: "PLAYGROUND"},
        ]],
    ])("requests the error logs of a %s execution with the matching filters", async (kind, filters) => {
        // Given
        const loadLogs = vi.spyOn(useExecutionsStore(), "loadLogs").mockResolvedValue(errorLogs as never)

        // When
        i18nMount(ErrorAlert, {messages, props: {execution: {...execution, kind} as Execution}})
        await flushPromises()

        // Then
        expect(loadLogs).toHaveBeenCalledWith(expect.objectContaining({filters}))
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
