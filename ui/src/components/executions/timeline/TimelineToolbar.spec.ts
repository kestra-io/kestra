import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import TimelineToolbar from "./TimelineToolbar.vue"

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {
        ...actual,
        dateUtils: {dateFilter: (iso: string) => iso},
        durationUtils: {...actual.durationUtils, humanDuration: (seconds: number) => `${seconds}s`},
    }
})

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {executionsTimeline: {toolbar: {
        range: "{start} → {end} ({duration})",
    }}}},
})

const stubs = {
    KsButton: defineComponent({name: "KsButton", template: "<button type=\"button\"><slot /></button>"}),
    KsIconButton: defineComponent({name: "KsIconButton", template: "<button type=\"button\"><slot /></button>"}),
    KsText: defineComponent({name: "KsText", template: "<span><slot /></span>"}),
    TimelineScrubber: defineComponent({
        name: "TimelineScrubber",
        props: ["domainStartMs", "domainEndMs", "rangeStartMs", "rangeEndMs", "executions", "widthPx"],
        emits: ["change"],
        template: "<div data-test=\"range-scrubber\" />",
    }),
}

function mountToolbar(props: {rangeStartMs: number; rangeEndMs: number; expanded?: boolean}) {
    return mount(TimelineToolbar, {
        props: {expanded: false, domainExecutions: [], scrubberWidthPx: 600, ...props},
        global: {plugins: [i18n], stubs},
    })
}

describe("TimelineToolbar", () => {
    it("should show the window as a read-only readout and leave picking a range to the filter bar", () => {
        const wrapper = mountToolbar({rangeStartMs: 0, rangeEndMs: 60 * 60 * 1000})

        const readout = wrapper.get("[data-test=timeline-range-readout]")
        expect(readout.text()).toContain("→")
        expect(readout.element.tagName).not.toBe("BUTTON")
        expect(wrapper.findComponent({name: "KsPopover"}).exists()).toBe(false)
        expect(wrapper.findComponent({name: "KsDatePicker"}).exists()).toBe(false)
        expect(wrapper.findComponent({name: "TimeSelect"}).exists()).toBe(false)
    })

    it("should emit custom-range when the scrubber reports a finished brush", async () => {
        const wrapper = mountToolbar({rangeStartMs: 0, rangeEndMs: 60 * 60 * 1000})

        await wrapper.findComponent({name: "TimelineScrubber"}).vm.$emit("change", {startMs: 1000, endMs: 2000})

        expect(wrapper.emitted("custom-range")).toEqual([[{startMs: 1000, endMs: 2000}]])
    })
})
