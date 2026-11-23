import {describe, it, expect, vi} from "vitest"
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))

vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({generate: vi.fn(), chartPreview: vi.fn()}),
}))

import Markdown from "./Markdown.vue"
import en from "../../../translations/en.json"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import type {Chart} from "../types"

function mountMarkdown(chart: Chart) {
    return i18nMount(Markdown, {
        locales: en,
        props: {chart, filters: [], showDefault: false},
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
})