import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import TimelineToolbar from "./TimelineToolbar.vue"

// dateUtils.dateFilter reads Vue's $moment global property, wired up by the app plugin at bootstrap
// and absent in a bare component mount; stub both date/duration formatting deterministically.
vi.mock("@kestra-io/design-system", () => ({
    dateUtils: {dateFilter: (iso: string) => iso},
    durationUtils: {humanDuration: (seconds: number) => `${seconds}s`},
}))

// Statically imported (not resolved by name at runtime), so global.stubs can't intercept them —
// replace the modules directly instead.
vi.mock("../date-select/TimeSelect.vue", () => ({
    default: defineComponent({
        name: "TimeSelect",
        emits: ["update:modelValue"],
        template: "<button data-test=\"relative-preset\" @click=\"$emit('update:modelValue', {timeRange: 'PT1H'})\" />",
    }),
}))
vi.mock("../../layout/DateRange.vue", () => ({
    default: defineComponent({name: "DateRange", template: "<div data-test=\"absolute-picker\" />"}),
}))

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {executionsTimeline: {toolbar: {range: "{start} → {end} ({duration})"}}}},
})

const passthroughStub = (name: string, slots: string[] = ["default"]) => defineComponent({
    name,
    template: `<div>${slots.map(slot => slot === "default" ? "<slot />" : `<slot name="${slot}" />`).join("")}</div>`,
})

const stubs = {
    KsPopover: passthroughStub("KsPopover", ["reference", "default"]),
    KsButton: defineComponent({name: "KsButton", template: "<button type=\"button\"><slot /></button>"}),
    KsIconButton: defineComponent({name: "KsIconButton", template: "<button type=\"button\"><slot /></button>"}),
    KsRadioGroup: passthroughStub("KsRadioGroup"),
    KsRadioButton: passthroughStub("KsRadioButton"),
}

function mountToolbar(props: {rangeStartMs: number; rangeEndMs: number; activePreset?: string; expanded?: boolean}) {
    return mount(TimelineToolbar, {
        props: {expanded: false, ...props},
        global: {plugins: [i18n], stubs},
    })
}

describe("TimelineToolbar", () => {
    it("should show the current range as a single readable pill instead of separate preset/date controls", () => {
        const wrapper = mountToolbar({rangeStartMs: 0, rangeEndMs: 60 * 60 * 1000, activePreset: "PT1H"})

        expect(wrapper.find(".range-pill").text()).toContain("→")
        expect(wrapper.findComponent({name: "KsSelect"}).exists()).toBe(false)
        expect(wrapper.findComponent({name: "KsDatePicker"}).exists()).toBe(false)
    })

    it("should emit apply-preset when a relative range is picked from the combined picker", async () => {
        const wrapper = mountToolbar({rangeStartMs: 0, rangeEndMs: 60 * 60 * 1000, activePreset: "PT1H"})

        await wrapper.find("[data-test=relative-preset]").trigger("click")

        expect(wrapper.emitted("apply-preset")).toEqual([["PT1H"]])
    })

    it("should show the absolute date-range picker instead of the relative one when switched to Absolute", () => {
        const wrapper = mountToolbar({rangeStartMs: 0, rangeEndMs: 60 * 60 * 1000, activePreset: undefined})

        expect(wrapper.find("[data-test=absolute-picker]").exists()).toBe(true)
        expect(wrapper.find("[data-test=relative-preset]").exists()).toBe(false)
    })
})
