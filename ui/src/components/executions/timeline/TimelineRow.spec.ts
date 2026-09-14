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

    it("should collapse into a single lane instead of one sliver per run when a lane-packed burst would already bucket combined", () => {
        const overlapping: TimelineExecution[] = Array.from({length: 25}, (_, i) => ({
            id: `exec-${i}`,
            namespace: "company.team",
            flowId: "flow",
            state: "SUCCESS",
            startMs: i,
            endMs: i + 1,
        }))

        const wrapper = mount(TimelineRow, {
            props: {
                label: "flow",
                executions: overlapping,
                rangeStartMs: 0,
                rangeEndMs: 100_000,
                availableWidthPx: 600,
                packLanes: true,
                dimmedStates: new Set<string>(),
            },
            global: {plugins: [i18n], stubs},
        })

        expect(wrapper.findAll(".timeline-lane")).toHaveLength(1)
    })

    it("should keep separate lanes for genuinely overlapping runs when zoomed in enough to tell them apart", () => {
        const overlapping: TimelineExecution[] = Array.from({length: 3}, (_, i) => ({
            id: `exec-${i}`,
            namespace: "company.team",
            flowId: "flow",
            state: "SUCCESS",
            startMs: i * 400,
            endMs: i * 400 + 1000,
        }))

        const wrapper = mount(TimelineRow, {
            props: {
                label: "flow",
                executions: overlapping,
                rangeStartMs: 0,
                rangeEndMs: 2000,
                availableWidthPx: 600,
                packLanes: true,
                dimmedStates: new Set<string>(),
            },
            global: {plugins: [i18n], stubs},
        })

        expect(wrapper.findAll(".timeline-lane")).toHaveLength(3)
    })
})
