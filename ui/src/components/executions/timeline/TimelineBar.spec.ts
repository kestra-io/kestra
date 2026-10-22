import {describe, expect, it, test, vi} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {defineComponent} from "vue"
import TimelineBar from "./TimelineBar.vue"
import type {StateBucket, TimelineExecution} from "../../../utils/executionsTimeline"

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {
        ...actual,
        dateUtils: {...actual.dateUtils, dateFilter: (iso: string) => iso},
        durationUtils: {...actual.durationUtils, humanDuration: (seconds: number) => `${seconds}s`},
    }
})


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

const placementAwareStubs = {
    ...popoverAwareStubs,
    KsPopover: {
        props: ["visible", "placement"],
        emits: ["update:visible"],
        template: `
            <div>
                <div data-test="reference" @click="$emit('update:visible', !visible)">
                    <slot name="reference" />
                </div>
                <div v-if="visible" data-test="popover-body" :data-placement="placement"><slot /></div>
            </div>
        `,
    },
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
    return i18nMount(TimelineBar, {
        props: {leftPercent: 0, widthPercent: 10, dimmed: false, ...props},
        global: {stubs},
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
        const wrapper = i18nMount(TimelineBar, {
            props: {leftPercent: 0, widthPercent: 10, dimmed: false, execution: clickedExecution},
            global: {stubs: {KsTooltip: true, KsDateAgo: true}},
            attachTo: document.body,
        })
        expect(document.querySelector(".timeline-bar-popover")).toBeNull()

        await wrapper.get(".timeline-bar").trigger("click")
        await vi.waitFor(() => expect(document.querySelector(".timeline-bar-popover")).not.toBeNull())

        const popover = document.querySelector(".timeline-bar-popover")
        expect(popover).not.toBeNull()
        expect(popover!.textContent).toContain("my-flow")
        wrapper.unmount()
        document.body.innerHTML = ""
    })

    test("labels the bar with its flow and readable state", () => {
        const wrapper = mountBar({execution: {...execution, state: "KILLED"}})

        expect(wrapper.get(".timeline-bar").attributes("aria-label")).toBe("executionsTimeline.bar.label")
    })

    test("lists a bucket's states by their readable names", () => {
        const wrapper = i18nMount(TimelineBar, {
            messages: {executionsTimeline: {bucket: {stateCount: "{state}: {count}", hint: "hint"}, row: {runsCount: "{count} run | {count} runs"}}},
            props: {leftPercent: 0, widthPercent: 10, dimmed: false, bucket},
            global: {stubs},
        })

        expect(wrapper.text()).toContain("10 runs")
        expect(wrapper.text()).toContain("Success: 8")
        expect(wrapper.text()).toContain("Failed: 2")
        expect(wrapper.text()).not.toContain("SUCCESS")
    })

    test("flips the popover to bottom when the bar is near the top of the timeline card", async () => {
        const card = document.createElement("div")
        card.className = "executions-timeline"
        document.body.appendChild(card)

        const wrapper = i18nMount(TimelineBar, {
            props: {leftPercent: 0, widthPercent: 10, dimmed: false, execution},
            global: {stubs: placementAwareStubs},
            attachTo: card,
        })

        await wrapper.get(".timeline-bar").trigger("click")

        expect(wrapper.get("[data-test=\"popover-body\"]").attributes("data-placement")).toBe("bottom")

        card.remove()
    })

    test("keeps the popover on top when there is plenty of room above the bar", async () => {
        const card = document.createElement("div")
        card.className = "executions-timeline"
        document.body.appendChild(card)

        const wrapper = i18nMount(TimelineBar, {
            props: {leftPercent: 0, widthPercent: 10, dimmed: false, execution},
            global: {stubs: placementAwareStubs},
            attachTo: card,
        })

        vi.spyOn(wrapper.get(".timeline-bar").element, "getBoundingClientRect").mockReturnValue({top: 500} as DOMRect)

        await wrapper.get(".timeline-bar").trigger("click")

        expect(wrapper.get("[data-test=\"popover-body\"]").attributes("data-placement")).toBe("top")

        card.remove()
    })
})
