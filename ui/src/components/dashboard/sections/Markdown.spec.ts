import {describe, it, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {KsSkeleton} from "@kestra-io/design-system"
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))

const chartPreview = vi.hoisted(() => vi.fn())
vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({generate: vi.fn(), chartPreview}),
}))

import Markdown from "./Markdown.vue"
import en from "../../../translations/en.json"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import type {Chart} from "../types"

function mountMarkdown(chart: Chart, showDefault = false) {
    return i18nMount(Markdown, {
        locales: en,
        props: {chart, filters: [], showDefault},
    })
}

describe("dashboard sections/Markdown.vue", () => {
    it("renders the chart's source.content", async () => {
        const wrapper = mountMarkdown({
            id: "notes",
            type: "io.kestra.plugin.core.dashboard.chart.Markdown",
            source: {type: "Text", content: "## Export check"},
        })

        // KsMarkdown is an async component; wait for its loader to resolve.
        await vi.dynamicImportSettled()

        expect(wrapper.text()).toContain("Export check")
    })

    // Regression guard: Dashboard.vue and the EE editor previews all stamp every
    // loaded chart with `content: <yaml dump of the whole chart>` to support ad-hoc chart
    // preview submission for non-Markdown chart types. A Markdown chart must ignore that
    // stamped `content` and always render its own `source.content`.
    it("prefers source.content over a stamped whole-chart content field", async () => {
        const chart = {
            id: "notes",
            type: "io.kestra.plugin.core.dashboard.chart.Markdown",
            source: {type: "Text", content: "## Export check"},
        }
        const wrapper = mountMarkdown({...chart, content: "id: notes\ntype: io.kestra.plugin.core.dashboard.chart.Markdown\nsource:\n  type: Text\n  content: \"## Export check\""})

        // KsMarkdown is an async component; wait for its loader to resolve.
        await vi.dynamicImportSettled()

        expect(wrapper.text()).toContain("Export check")
        expect(wrapper.text()).not.toContain("chartOptions")
        expect(wrapper.text()).not.toContain("io.kestra.plugin.core.dashboard.chart.Markdown")
    })

    it("shows a skeleton, not the empty state, while the flow description loads", async () => {
        let answer: (value: unknown) => void = () => {}
        chartPreview.mockReturnValueOnce(new Promise((resolve) => answer = resolve))
        const wrapper = mountMarkdown({
            id: "description",
            type: "io.kestra.plugin.core.dashboard.chart.Markdown",
            source: {type: "FlowDescription", namespace: "company.team", flowId: "described"},
            content: "id: description",
        }, true)
        await flushPromises()

        expect(wrapper.findComponent(KsSkeleton).exists()).toBe(true)
        expect(wrapper.text()).not.toContain("No description")

        answer({results: [{description: "## Described flow"}]})
        await flushPromises()
        await vi.dynamicImportSettled()

        expect(wrapper.findComponent(KsSkeleton).exists()).toBe(false)
        expect(wrapper.text()).toContain("Described flow")
    })
})
