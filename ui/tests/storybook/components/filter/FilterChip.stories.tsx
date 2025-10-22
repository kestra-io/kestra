import {ref} from "vue";
import {within, userEvent, expect, waitFor} from "storybook/test";
import type {Meta, StoryObj} from "@storybook/vue3-vite";

import FilterChip from "../../../../src/components/filter/components/layout/FilterChip.vue";

import {AppliedFilter, Comparators, FilterKeyConfig, FilterValue} from "../../../../src/components/filter/utils/filterTypes";
import {useValues} from "../../../../src/components/filter/composables/useValues";

interface StoryFilter extends AppliedFilter {
    filterKey: FilterKeyConfig;
}

const meta: Meta<typeof FilterChip> = {
    title: "Components/Filter/FilterChip",
    component: FilterChip,
    parameters: {
        layout: "padded",
    },
}

export default meta;

type Story = StoryObj<typeof FilterChip>;

const mockFilterKeys = {
    text: {
        key: "namespace",
        label: "Namespace",
        description: "Filter by namespace",
        comparators: [Comparators.EQUALS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.CONTAINS],
        showComparatorSelection: true,
        valueType: "text",
    } as FilterKeyConfig,
    select: {
        key: "state",
        label: "State",
        description: "Filter by execution state",
        comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
        valueType: "select",
        valueProvider: async (): Promise<FilterValue[]> => {
            const {VALUES} = useValues("executions");
            return VALUES.EXECUTION_STATES;
        },
    } as FilterKeyConfig,
    multiSelect: {
        key: "labels",
        label: "Labels",
        description: "Filter by labels",
        comparators: [Comparators.IN, Comparators.NOT_IN],
        valueType: "multi-select",
        searchable: true,
        valueProvider: async (): Promise<FilterValue[]> => [
            {label: "Production", value: "prod"},
            {label: "Staging", value: "staging"},
            {label: "Development", value: "dev"},
            {label: "Testing", value: "test"},
        ],
    } as FilterKeyConfig,
    details: {
        key: "details",
        label: "Details",
        description: "Filter by key-value pairs",
        comparators: [Comparators.IN],
        valueType: "details",
    } as FilterKeyConfig,
    radio: {
        key: "child",
        label: "Child",
        description: "Filter by execution type",
        comparators: [Comparators.EQUALS],
        valueType: "radio",
        valueProvider: async (): Promise<FilterValue[]> => {
            const {VALUES} = useValues("executions");
            return VALUES.CHILDS;
        },
    } as FilterKeyConfig,
    timeRange: {
        key: "timeRange",
        label: "Time Range",
        description: "Filter by time range",
        comparators: [Comparators.IN],
        valueType: "select",
        valueProvider: async (): Promise<FilterValue[]> => {
            const {VALUES} = useValues("executions");
            return VALUES.RELATIVE_DATE;
        },
    } as FilterKeyConfig,
};

const render: Story["render"] = (args) => ({
    setup() {
        const filterRef = ref(args.filter);
        const filterKeyRef = ref(args.filterKey);

        const handleUpdate = (updatedFilter: AppliedFilter) => {
            filterRef.value = updatedFilter;
        };

        const handleRemove = (filterId: string) => {
            console.log("Filter removed:", filterId);
        };

        return () => (
            <div style="padding: 2rem; background: var(--ks-background-body);">
                <FilterChip
                    filter={filterRef.value}
                    filterKey={filterKeyRef.value}
                    onUpdate={handleUpdate}
                    onRemove={handleRemove}
                />
            </div>
        );
    },
});

const createPlayFunction = (chipText: string) => async ({canvasElement}: {canvasElement: HTMLElement}) => {
    const canvas = within(canvasElement);
    const chip = await canvas.findByText(chipText);

    await userEvent.click(chip);

    await waitFor(
        () => {
            const popper = document.querySelector(".edit-popper");
            expect(popper).toBeInTheDocument();
        },
        {timeout: 3000}
    );
};

export const TextFilter: Story = {
    render,
    args: {
        filter: {
            id: "1",
            key: "namespace",
            keyLabel: "Namespace",
            comparator: Comparators.STARTS_WITH,
            comparatorLabel: "Starts With",
            value: "io.kestra",
            valueLabel: "io.kestra",
        },
        filterKey: mockFilterKeys.text,
    },
    play: createPlayFunction("Namespace"),
};

export const SelectFilter: Story = {
    render,
    args: {
        filter: {
            id: "2",
            key: "state",
            keyLabel: "State",
            comparator: Comparators.EQUALS,
            comparatorLabel: "Equals",
            value: "SUCCESS",
            valueLabel: "Success",
        },
        filterKey: mockFilterKeys.select,
    },
    play: createPlayFunction("State"),
};

export const MultiSelectFilter: Story = {
    render,
    args: {
        filter: {
            id: "3",
            key: "labels",
            keyLabel: "Labels",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: ["prod", "staging"],
            valueLabel: "Production, Staging",
        },
        filterKey: mockFilterKeys.multiSelect,
    },
    play: createPlayFunction("Labels"),
};

export const DetailsFilter: Story = {
    render,
    args: {
        filter: {
            id: "5",
            key: "details",
            keyLabel: "Details",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: ["env:production", "region:us-west"],
            valueLabel: "2 Details key/value pairs",
        },
        filterKey: mockFilterKeys.details,
    },
    play: createPlayFunction("Details"),
};

export const RadioFilter: Story = {
    render,
    args: {
        filter: {
            id: "6",
            key: "child",
            keyLabel: "Child",
            comparator: Comparators.EQUALS,
            comparatorLabel: "Equals",
            value: "CHILD",
            valueLabel: "CHILD",
        },
        filterKey: mockFilterKeys.radio,
    },
    play: createPlayFunction("Child"),
};

export const TimeRangeSelectFilter: Story = {
    render,
    args: {
        filter: {
            id: "7",
            key: "timeRange",
            keyLabel: "Time Range",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: "PT24H",
            valueLabel: "Last 24 Hours",
        },
        filterKey: mockFilterKeys.timeRange,
    },
    play: createPlayFunction("Time Range"),
};

export const AllLayout: Story = {
    render: () => ({
        setup() {
            const filters = ref<StoryFilter[]>([
                {
                    id: "1",
                    key: "namespace",
                    keyLabel: "Namespace",
                    comparator: Comparators.STARTS_WITH,
                    comparatorLabel: "Starts With",
                    value: "io.kestra",
                    valueLabel: "io.kestra",
                    filterKey: mockFilterKeys.text,
                },
                {
                    id: "2",
                    key: "state",
                    keyLabel: "State",
                    comparator: Comparators.EQUALS,
                    comparatorLabel: "Equals",
                    value: "SUCCESS",
                    valueLabel: "Success",
                    filterKey: mockFilterKeys.select,
                },
                {
                    id: "3",
                    key: "labels",
                    keyLabel: "Labels",
                    comparator: Comparators.IN,
                    comparatorLabel: "In",
                    value: ["prod", "staging"],
                    valueLabel: "Production, Staging",
                    filterKey: mockFilterKeys.multiSelect,
                },
                {
                    id: "5",
                    key: "details",
                    keyLabel: "Details",
                    comparator: Comparators.IN,
                    comparatorLabel: "In",
                    value: ["env:production"],
                    valueLabel: "env:production",
                    filterKey: mockFilterKeys.details,
                },
                {
                    id: "6",
                    key: "child",
                    keyLabel: "Child",
                    comparator: Comparators.EQUALS,
                    comparatorLabel: "Equals",
                    value: "CHILD",
                    valueLabel: "CHILD",
                    filterKey: mockFilterKeys.radio,
                },
                {
                    id: "7",
                    key: "timeRange",
                    keyLabel: "Time Range",
                    comparator: Comparators.IN,
                    comparatorLabel: "In",
                    value: "PT24H",
                    valueLabel: "Last 24 Hours",
                    filterKey: mockFilterKeys.timeRange,
                },
                {
                    id: "8",
                    key: "timeRange",
                    keyLabel: "Time Range",
                    comparator: Comparators.IN,
                    comparatorLabel: "In",
                    value: {
                        startDate: new Date("2024-01-01"),
                        endDate: new Date("2024-12-31"),
                    },
                    valueLabel: "1/1/2024 - 12/31/2024",
                    filterKey: mockFilterKeys.timeRange,
                },
            ]);

            const handleUpdate = (updatedFilter: AppliedFilter, index: number) => {
                const currentFilter = filters.value[index];
                filters.value[index] = {
                    ...currentFilter,
                    ...updatedFilter,
                    filterKey: currentFilter.filterKey,
                } as StoryFilter;
            };

            const handleRemove = (filterId: string) => {
                filters.value = filters.value.filter(f => f.id !== filterId);
            };

            return () => (
                <div style="padding: 2rem; background: var(--ks-background-body);">
                    <h3 style="margin-bottom: 1rem; color: var(--ks-content-primary);">
                        <strong>Filter Poppers Layout</strong>
                    </h3>
                    <div style="display: flex; flex-wrap: wrap; gap: 1rem;">
                        {filters.value.map((filter, index) => (
                            <FilterChip
                                key={filter.id}
                                filter={filter}
                                filterKey={filter.filterKey}
                                onUpdate={(updated) => handleUpdate(updated, index)}
                                onRemove={handleRemove}
                            />
                        ))}
                    </div>
                </div>
            );
        },
    }),
};
