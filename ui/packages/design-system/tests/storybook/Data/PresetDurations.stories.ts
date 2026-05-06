import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {computed, ref} from "vue"
import PresetDurations from "../../../src/components/Data/KsDataTable/filter/layout/PresetDurations.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import {Comparators, type AppliedFilter, type FilterConfiguration} from "../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const meta: Meta<typeof PresetDurations> = {
    title: "Components/Data/PresetDurations",
    component: PresetDurations,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "Quick-interval chip strip for time-range filters. Renders inside a KsFilter context — pass `presetDurations` on the parent KsFilter rather than mounting this directly.",
            },
        },
    },
}
export default meta

type Story = StoryObj<typeof PresetDurations>

const PRESETS = [
    {label: "1h", value: "PT1H"},
    {label: "24h", value: "PT24H"},
    {label: "7d", value: "PT168H"},
    {label: "30d", value: "PT720H"},
]

const TIME_RANGE_CONFIGURATION: FilterConfiguration = {
    title: "Filters",
    keys: [
        {
            key: "timeRange",
            label: "Time range",
            comparators: [Comparators.EQUALS],
            valueType: "select",
            valueProvider: async () => PRESETS,
        },
    ],
}

function buildContext(initial?: string) {
    const applied = ref<AppliedFilter[]>(
        initial
            ? [
                {
                    id: `timeRange-${initial}`,
                    key: "timeRange",
                    keyLabel: "Time range",
                    valueLabel: PRESETS.find((p) => p.value === initial)?.label ?? initial,
                    comparator: Comparators.EQUALS,
                    comparatorLabel: "Equals",
                    value: initial,
                },
            ]
            : [],
    )

    return {
        configuration: computed(() => TIME_RANGE_CONFIGURATION),
        appliedFilters: applied,
        readOnly: computed(() => false),
        addFilter: (f: AppliedFilter) => {
            applied.value = [...applied.value.filter((x) => x.key !== f.key), f]
        },
    }
}

export const Default: Story = {
    render: () => ({
        components: {PresetDurations},
        setup() {
            return {presets: PRESETS}
        },
        provide() {
            return {[FILTER_CONTEXT_INJECTION_KEY]: buildContext()}
        },
        template: "<div style=\"padding:24px\"><preset-durations :presets=\"presets\" /></div>",
    }),
}

export const WithActiveSelection: Story = {
    render: () => ({
        components: {PresetDurations},
        setup() {
            return {presets: PRESETS}
        },
        provide() {
            return {[FILTER_CONTEXT_INJECTION_KEY]: buildContext("PT24H")}
        },
        template: "<div style=\"padding:24px\"><preset-durations :presets=\"presets\" /></div>",
    }),
}
