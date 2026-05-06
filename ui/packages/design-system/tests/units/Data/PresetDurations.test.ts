import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {computed, ref} from "vue"
import KestraDesignSystem from "../../../src/index"
import PresetDurations from "../../../src/components/Data/KsDataTable/filter/layout/PresetDurations.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import {Comparators, type AppliedFilter, type FilterConfiguration} from "../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const globalConfig = {plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem]}

const PRESETS = [
    {label: "1h", value: "PT1H"},
    {label: "24h", value: "PT24H"},
    {label: "7d", value: "PT168H"},
]

function buildFilterContext(opts: {
    appliedFilters?: AppliedFilter[];
    addFilter?: (f: AppliedFilter) => void;
    readOnly?: boolean;
    hasTimeRangeKey?: boolean;
}) {
    const configuration: FilterConfiguration = {
        title: "Test",
        keys: opts.hasTimeRangeKey === false ? [] : [
            {
                key: "timeRange",
                label: "Time range",
                comparators: [Comparators.EQUALS],
                valueType: "select",
                valueProvider: async () => [],
            },
        ],
    }

    return {
        configuration: computed(() => configuration),
        appliedFilters: ref<AppliedFilter[]>(opts.appliedFilters ?? []),
        addFilter: opts.addFilter ?? vi.fn(),
        readOnly: computed(() => opts.readOnly ?? false),
    }
}

function mountPresetDurations(context: ReturnType<typeof buildFilterContext>, presets = PRESETS) {
    return mount(PresetDurations, {
        props: {presets},
        global: {
            ...globalConfig,
            provide: {[FILTER_CONTEXT_INJECTION_KEY as symbol]: context},
        },
    })
}

const root = (wrapper: ReturnType<typeof mountPresetDurations>) =>
    wrapper.find("[data-test=\"preset-durations\"]")
const radios = (wrapper: ReturnType<typeof mountPresetDurations>) =>
    wrapper.findAll("[data-test=\"preset-durations\"] input[type=\"radio\"]")
const labels = (wrapper: ReturnType<typeof mountPresetDurations>) =>
    wrapper.findAll("[data-test=\"preset-durations\"] label")

describe("PresetDurations", () => {
    test("renders one segment per preset", () => {
        const wrapper = mountPresetDurations(buildFilterContext({}))
        expect(root(wrapper).exists()).toBe(true)
        const segments = labels(wrapper)
        expect(segments).toHaveLength(PRESETS.length)
        expect(segments[0].text()).toBe("1h")
        expect(segments[2].text()).toBe("7d")
    })

    test("marks the segment matching the current timeRange filter as selected", async () => {
        const applied: AppliedFilter = {
            id: "tr",
            key: "timeRange",
            keyLabel: "Time range",
            valueLabel: "24h",
            comparator: Comparators.EQUALS,
            comparatorLabel: "Equals",
            value: "PT24H",
        }
        const wrapper = mountPresetDurations(buildFilterContext({appliedFilters: [applied]}))
        await wrapper.vm.$nextTick()
        const selected = radios(wrapper).filter((r) => (r.element as HTMLInputElement).checked)
        expect(selected).toHaveLength(1)
        expect(selected[0].attributes("value") ?? selected[0].element.parentElement?.textContent?.trim()).toBe("24h")
    })

    test("emits addFilter when an inactive segment is selected", async () => {
        const addFilter = vi.fn()
        const wrapper = mountPresetDurations(buildFilterContext({addFilter}))
        await radios(wrapper)[1].setValue(true)
        expect(addFilter).toHaveBeenCalledTimes(1)
        const applied = addFilter.mock.calls[0][0] as AppliedFilter
        expect(applied.key).toBe("timeRange")
        expect(applied.value).toBe("PT24H")
        expect(applied.comparator).toBe(Comparators.EQUALS)
    })

    test("does not addFilter when selecting the already-active segment", async () => {
        const addFilter = vi.fn()
        const applied: AppliedFilter = {
            id: "tr",
            key: "timeRange",
            keyLabel: "Time range",
            valueLabel: "24h",
            comparator: Comparators.EQUALS,
            comparatorLabel: "Equals",
            value: "PT24H",
        }
        const wrapper = mountPresetDurations(buildFilterContext({appliedFilters: [applied], addFilter}))
        await wrapper.vm.$nextTick()
        await radios(wrapper)[1].setValue(true)
        expect(addFilter).not.toHaveBeenCalled()
    })

    test("shows nothing selected when current timeRange does not match any preset", async () => {
        const applied: AppliedFilter = {
            id: "tr",
            key: "timeRange",
            keyLabel: "Time range",
            valueLabel: "12h",
            comparator: Comparators.EQUALS,
            comparatorLabel: "Equals",
            value: "PT12H",
        }
        const wrapper = mountPresetDurations(buildFilterContext({appliedFilters: [applied]}))
        await wrapper.vm.$nextTick()
        const selected = radios(wrapper).filter((r) => (r.element as HTMLInputElement).checked)
        expect(selected).toHaveLength(0)
    })
})
