import {describe, expect, it, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import TimelineBar from "./TimelineBar.vue"
import type {StateBucket, TimelineExecution} from "../../../utils/executionsTimeline"

// dateUtils.dateFilter reads Vue's $moment global property, wired up by the app plugin at bootstrap
// and absent in a bare component mount; stub it with a deterministic formatter instead.
vi.mock("@kestra-io/design-system", () => ({
    dateUtils: {dateFilter: (iso: string) => iso},
    durationUtils: {humanDuration: (seconds: number) => `${seconds}s`},
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
    KsDateAgo: passthroughStub("KsDateAgo"),
}

// KsPopover's own trigger element is the single node passed in the #reference slot - this stub
// mirrors that contract so a regression that stacks another trigger on top (the ElOnlyChild
// composition bug this guards against) would render a second element.
const popoverAwareStubs = {
    ...stubs,
    KsPopover: {
        props: ["visible"],
        emits: ["update:visible"],
        template: `
            <div>
                <div data-test="reference" @click="$emit('update:visible', !visible)">
                    <slot name="reference" />
                </div>
                <div v-if="visible" data-test="popover-body"><slot /></div>
            </div>
        `,
    },
    KsTooltip: {
        props: ["visible"],
        template: "<div v-if=\"visible\" data-test=\"tooltip-body\"><slot name=\"content\" /></div>",
    },
    KsId: {props: ["value"], template: "<span>{{ value }}</span>"},
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

    test("renders a single clickable bar element, not a duplicated trigger wrapper", () => {
        const wrapper = mountBar({execution})

        const bars = wrapper.findAll(".timeline-bar")
        expect(bars).toHaveLength(1)
        expect(bars[0].element.tagName).toBe("BUTTON")
    })

    test("opens the execution detail popover when the bar is clicked", async () => {
        const clickedExecution: TimelineExecution = {...execution, id: "execution-id", flowId: "my-flow"}
        const wrapper = mount(TimelineBar, {
            props: {leftPercent: 0, widthPercent: 10, dimmed: false, execution: clickedExecution},
            global: {plugins: [i18n], stubs: popoverAwareStubs},
        })
        expect(wrapper.find("[data-test=\"popover-body\"]").exists()).toBe(false)

        await wrapper.get(".timeline-bar").trigger("click")

        expect(wrapper.find("[data-test=\"popover-body\"]").exists()).toBe(true)
        expect(wrapper.text()).toContain("execution-id")
        expect(wrapper.text()).toContain("my-flow")
    })
})
