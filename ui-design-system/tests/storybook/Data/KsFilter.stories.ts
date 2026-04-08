import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsFilter from "../../../src/components/Data/KsDataTable/KsFilter.vue"

const meta: Meta<typeof KsFilter> = {
    title: "Components/Data/KsFilter",
    component: KsFilter,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsFilter provides a complete filter bar with search, filter chips, saved filters, and table options.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsFilter>

const SIMPLE_CONFIGURATION = {
    searchPlaceholder: "Search...",
    keys: [
        {
            key: "status",
            label: "Status",
            valueType: "select",
            comparators: ["="],
            values: [
                {label: "Success", value: "SUCCESS"},
                {label: "Failed", value: "FAILED"},
                {label: "Running", value: "RUNNING"},
            ],
        },
        {
            key: "namespace",
            label: "Namespace",
            valueType: "text",
            comparators: ["*=", "^="],
        },
    ],
}

export const Default: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            return {configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter :configuration="configuration" />
            </div>
        `,
    }),
}

export const ReadOnly: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            return {configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter :configuration="configuration" :readOnly="true" />
            </div>
        `,
    }),
}

export const WithoutSearch: Story = {
    render: () => ({
        components: {KsFilter},
        setup() {
            return {configuration: SIMPLE_CONFIGURATION}
        },
        template: `
            <div style="padding: 24px">
                <KsFilter :configuration="configuration" :showSearchInput="false" />
            </div>
        `,
    }),
}
