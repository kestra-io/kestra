import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import TimelineRow from "./TimelineRow.vue"
import type {TimelineExecution} from "../../../utils/executionsTimeline"

const i18n = createI18n({legacy: false, globalInjection: true, locale: "en", messages: {en: {}}})

const stubs = {
    KsIconButton: defineComponent({name: "KsIconButton", template: "<button type=\"button\"><slot /></button>"}),
    TimelineBar: defineComponent({name: "TimelineBar", template: "<div />"}),
}

const executions: TimelineExecution[] = [
    {id: "1", namespace: "company.team", flowId: "flow", state: "SUCCESS", startMs: 0, endMs: 1000},
]

function mountRow() {
    return mount(TimelineRow, {
        props: {
            label: "company.team",
            executions,
            rangeStartMs: 0,
            rangeEndMs: 10_000,
            availableWidthPx: 600,
            packLanes: false,
            dimmedStates: new Set<string>(),
        },
        global: {plugins: [i18n], stubs},
    })
}

describe("TimelineRow", () => {
    it("should expose the row label as a real button instead of a div faking one", () => {
        const wrapper = mountRow()

        const nameButton = wrapper.find(".name-button")
        expect(nameButton.element.tagName).toBe("BUTTON")
        expect(wrapper.find("[role=button]").exists()).toBe(false)
    })

    it("should emit drill-in when the row label button is clicked", async () => {
        const wrapper = mountRow()

        await wrapper.find(".name-button").trigger("click")

        expect(wrapper.emitted("drill-in")).toHaveLength(1)
    })
})
