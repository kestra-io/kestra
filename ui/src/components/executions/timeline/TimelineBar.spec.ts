import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import TimelineBar from "./TimelineBar.vue"
import type {StateBucket, TimelineExecution} from "../../../utils/executionsTimeline"

// dateUtils.dateFilter reads Vue's $moment global property, wired up by the app plugin at bootstrap
// and absent in a bare component mount; stub it with a deterministic formatter instead.
vi.mock("@kestra-io/design-system", () => ({
    dateUtils: {dateFilter: (iso: string) => iso},
}))

const i18n = createI18n({legacy: false, globalInjection: true, locale: "en", messages: {en: {}}})

const passthroughStub = (name: string, slots: string[] = ["default"]) => defineComponent({
    name,
    template: `<div>${slots.map(slot => slot === "default" ? "<slot />" : `<slot name="${slot}" />`).join("")}</div>`,
})

const stubs = {
    KsPopover: passthroughStub("KsPopover", ["reference", "default"]),
    KsTooltip: passthroughStub("KsTooltip", ["content", "default"]),
    KsId: passthroughStub("KsId"),
    KsButton: passthroughStub("KsButton"),
    KsExecutionStatus: passthroughStub("KsExecutionStatus"),
}

const execution: TimelineExecution = {
    id: "exec-1",
    namespace: "company.team",
    flowId: "flow",
    state: "SUCCESS",
    startMs: 0,
    endMs: 1000,
}

const bucket: StateBucket = {
    startMs: 0,
    endMs: 1000,
    total: 10,
    byState: {SUCCESS: 8, FAILED: 2},
    dominantState: "SUCCESS",
}

interface BarProps {
    execution?: TimelineExecution;
    bucket?: StateBucket;
    intensity?: number;
}

function mountBar(props: BarProps) {
    return mount(TimelineBar, {
        props: {leftPercent: 0, widthPercent: 10, dimmed: false, ...props},
        global: {plugins: [i18n], stubs},
    })
}

describe("TimelineBar", () => {
    it("should render a real execution bar with a per-state data attribute", () => {
        const wrapper = mountBar({execution})

        const bar = wrapper.find(".timeline-bar")
        expect(bar.attributes("data-state")).toBe("SUCCESS")
        expect(bar.attributes("data-bucket-state")).toBeUndefined()
    })

    it("should render a bucket with a tinted color-mix background instead of the flat state color", () => {
        const wrapper = mountBar({bucket, intensity: 0.5})

        const bucketEl = wrapper.find(".timeline-bucket")
        expect(bucketEl.attributes("data-state")).toBeUndefined()
        expect(bucketEl.attributes("data-bucket-state")).toBe("SUCCESS")
        expect(bucketEl.attributes("style")).toContain("color-mix")
        expect(bucketEl.attributes("style")).toContain("var(--ks-chart-success)")
    })

    it("should blend a denser bucket more strongly toward its tint", () => {
        const light = mountBar({bucket, intensity: 0})
        const dense = mountBar({bucket, intensity: 1})

        const lightMix = light.find(".timeline-bucket").attributes("style")!
        const denseMix = dense.find(".timeline-bucket").attributes("style")!

        expect(lightMix).toContain("30%")
        expect(denseMix).toContain("100%")
    })
})
