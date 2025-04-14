import type {Meta, StoryObj} from "@storybook/vue3";
import Card from "../../../../../src/components/dashboard/components/Card.vue";
import AccountMultiple from "vue-material-design-icons/AccountMultiple.vue";
import ChartTimelineVariant from "vue-material-design-icons/ChartTimelineVariant.vue";

// Meta information for the component
const meta = {
    title: "Components/Dashboard/Card",
    component: Card,
    tags: ["autodocs"],
    argTypes: {
        icon: {
            control: "text",
            description: "Icon component to display"
        },
        label: {
            control: "text",
            description: "Label text for the card"
        },
        tooltip: {
            control: "text",
            description: "Optional tooltip text"
        },
        value: {
            control: "text",
            description: "Main value to display"
        },
        redirect: {
            control: "object",
            description: "Vue Router location object for navigation"
        }
    }
} satisfies Meta<typeof Card>;

export default meta;
type Story = StoryObj<typeof meta>;

// Basic story
export const Default: Story = {
    args: {
        icon: AccountMultiple,
        label: "Total Users",
        value: "1,234",
        redirect: {name: "users"}
    }
};

// Story with tooltip
export const WithTooltip: Story = {
    args: {
        icon: ChartTimelineVariant,
        label: "Active Flows",
        tooltip: "Number of flows that have been executed in the last 24 hours",
        value: "42",
        redirect: {name: "flows"}
    }
};

// Story with number value
export const NumericValue: Story = {
    args: {
        icon: AccountMultiple,
        label: "New Users",
        value: 567,
        redirect: {name: "users"}
    }
}; 

export const ThreeCardsInRow = {
    render: () => ({
        setup() {
            return () => <div class="d-flex gap-3">
                <Card {...Default.args} />
                <Card {...WithTooltip.args} />
                <Card {...NumericValue.args} />
            </div>
        }
    })
}