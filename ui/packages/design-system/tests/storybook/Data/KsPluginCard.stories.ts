import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsPluginCard from "../../../src/components/Data/KsPluginCard.vue"

const meta: Meta<typeof KsPluginCard> = {
    title: "Components/Data/KsPluginCard",
    component: KsPluginCard,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsPluginCard renders a plugin / subgroup / task / blueprint card with optional icon, categories, and task/blueprint counts. The card is clickable as a whole and emits `@click`. Information shown adapts to the props provided — missing counts, categories, or description are simply omitted.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsPluginCard>

const wrap = (inner: string) => `
    <div style="padding:24px;max-width:320px">
        ${inner}
    </div>
`

export const Default: Story = {
    args: {
        title: "BigQuery",
        description: "Query, load and export data with Google BigQuery.",
        categories: ["DATABASE", "CLOUD"],
        taskCount: 12,
        blueprintCount: 4,
    },
    render: (args) => ({
        components: {KsPluginCard},
        setup() { return {args} },
        template: wrap("<KsPluginCard v-bind=\"args\" />"),
    }),
}

export const WithoutCounts: Story = {
    args: {
        title: "OpenAI",
        description: "Interact with OpenAI's GPT and embeddings APIs.",
        categories: ["AI"],
    },
    render: (args) => ({
        components: {KsPluginCard},
        setup() { return {args} },
        template: wrap("<KsPluginCard v-bind=\"args\" />"),
    }),
}

export const TaskCountOnly: Story = {
    args: {
        title: "Subflow",
        description: "Trigger a subflow from this flow.",
        taskCount: 1,
    },
    render: (args) => ({
        components: {KsPluginCard},
        setup() { return {args} },
        template: wrap("<KsPluginCard v-bind=\"args\" />"),
    }),
}

export const BlueprintLike: Story = {
    args: {
        title: "ETL pipeline",
        description: "Extract from Postgres, transform with Python, load into Snowflake.",
    },
    render: (args) => ({
        components: {KsPluginCard},
        setup() { return {args} },
        template: wrap("<KsPluginCard v-bind=\"args\" />"),
    }),
}

export const LongContent: Story = {
    args: {
        title: "A very very very long plugin title that needs to be truncated",
        description: "A description that wraps over two lines and gets clamped if it exceeds the available height, demonstrating overflow handling for description text.",
        categories: ["DATABASE", "CLOUD", "INGESTION", "MESSAGING"],
        taskCount: 42,
        blueprintCount: 15,
    },
    render: (args) => ({
        components: {KsPluginCard},
        setup() { return {args} },
        template: wrap("<KsPluginCard v-bind=\"args\" />"),
    }),
}

export const NotClickable: Story = {
    args: {
        title: "Read-only card",
        description: "A card without click affordance — no chevron, no hover effect.",
        taskCount: 8,
        clickable: false,
    },
    render: (args) => ({
        components: {KsPluginCard},
        setup() { return {args} },
        template: wrap("<KsPluginCard v-bind=\"args\" />"),
    }),
}
