import {describe, it, expect, vi} from "vitest"
import {createI18n} from "vue-i18n"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"
import KsDropdown from "@kestra-io/design-system/components/Navigation/KsDropdown/KsDropdown.vue"
import KsButton from "@kestra-io/design-system/components/Basic/KsButton/KsButton.vue"
import KsTooltip from "@kestra-io/design-system/components/Feedback/KsTooltip.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))

vi.mock("../../../../src/stores/dashboard", () => ({
    useDashboardStore: () => ({export: vi.fn()}),
}))

vi.mock("../../../../src/components/dashboard/dashboard-types", () => ({
    TYPES: {"stub-type": {template: "<div />"}},
}))

import Sections from "../../../../src/components/dashboard/sections/Sections.vue"
import en from "../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

function mountSections(charts: any[]) {
    return mount(Sections, {
        props: {
            dashboard: {id: "default", title: "", deleted: false, charts},
            charts,
        },
        global: {plugins: [i18n, KestraDesignSystem]},
    })
}

describe("dashboard Sections.vue — export trigger", () => {
    it("keeps the export KsDropdown's trigger button tooltip-free, so its slot content stays a single root", () => {
        const wrapper = mountSections([{id: "c1", type: "stub-type", chartOptions: {width: 6}}])

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)

        const dropdown = tooltip.findComponent(KsDropdown)
        expect(dropdown.exists()).toBe(true)

        // Regression guard: a `:tooltip` prop on the trigger KsButton makes KsButton render
        // itself wrapped in its own KsTooltip, giving KsDropdown's slot content a non-single-
        // element root and breaking ElDropdown's click-trigger binding (dropdown never opens).
        // The tooltip must live on the KsTooltip wrapping KsDropdown, not on the button.
        const triggerButton = dropdown.findComponent(KsButton)
        expect(triggerButton.props("tooltip")).toBeUndefined()
        expect(triggerButton.attributes("aria-label")).toBe("Export")
    })

    it("renders no export trigger for a Markdown chart", () => {
        const wrapper = mountSections([{
            id: "notes",
            type: "io.kestra.plugin.core.dashboard.chart.Markdown",
            chartOptions: {width: 6},
        }])

        expect(wrapper.findComponent(KsDropdown).exists()).toBe(false)
    })
})
