import {describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {
        ...actual,
        dateUtils: {...actual.dateUtils, dateFilter: (value: string) => value},
    }
})

import TimelineBar from "../../../../../src/components/executions/timeline/TimelineBar.vue"
import type {TimelineExecution} from "../../../../../src/utils/executionsTimeline"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {
        en: {
            id: "ID",
            namespace: "Namespace",
            flow: "Flow",
            state: "State",
            "start date": "Start date",
            "end date": "End date",
            "executionsTimeline.popover.openExecution": "Open execution",
            "executionsTimeline.popover.showOnlyFlow": "Show only this flow",
        },
    },
})

const globalConfig = {
    plugins: [i18n],
    stubs: {
        // KsPopover's own trigger element is the single node passed in the #reference slot -
        // this stub mirrors that contract so a regression that stacks another trigger on top
        // (the ElOnlyChild composition bug this guards against) would render a second element.
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
        KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
        KsId: {props: ["value"], template: "<span>{{ value }}</span>"},
    },
}

function buildExecution(): TimelineExecution {
    return {
        id: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "my-flow",
        state: "SUCCESS",
        startMs: Date.parse("2026-01-01T00:00:00Z"),
        endMs: Date.parse("2026-01-01T00:01:00Z"),
    }
}

function mountTimelineBar() {
    return mount(TimelineBar, {
        props: {
            execution: buildExecution(),
            leftPercent: 10,
            widthPercent: 5,
            dimmed: false,
        },
        global: globalConfig,
    })
}

describe("TimelineBar", () => {
    test("renders a single clickable bar element, not a duplicated trigger wrapper", () => {
        const wrapper = mountTimelineBar()

        const bars = wrapper.findAll(".timeline-bar")
        expect(bars).toHaveLength(1)
        expect(bars[0].element.tagName).toBe("BUTTON")
    })

    test("opens the execution detail popover when the bar is clicked", async () => {
        const wrapper = mountTimelineBar()
        expect(wrapper.find("[data-test=\"popover-body\"]").exists()).toBe(false)

        await wrapper.get(".timeline-bar").trigger("click")

        expect(wrapper.find("[data-test=\"popover-body\"]").exists()).toBe(true)
        expect(wrapper.text()).toContain("execution-id")
        expect(wrapper.text()).toContain("my-flow")
    })
})
