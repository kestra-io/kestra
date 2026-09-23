import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsEmptyState from "../../../src/components/Data/KsEmptyState.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsEmptyState> = {
    title: "Components/Data/KsEmptyState",
    component: KsEmptyState,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "Onboarding empty state for surfaces that have no data yet. Renders a left-aligned artwork tile, title, description and an optional action button (slot). A `learnMore` URL adds a 'Learn more' button pointing at the feature's documentation.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsEmptyState>

export const Default: Story = {
    render: () => ({
        components: {KsEmptyState},
        template:
            "<ks-empty-state title=\"You have no items yet!\" description=\"Add an item to get started.\" />",
    }),
}

export const WithAction: Story = {
    render: () => ({
        components: {KsEmptyState, KsButton},
        template: `
            <ks-empty-state title="You have no apps yet!" description="Create an app to expose your flows as a hosted UI.">
                <template #action>
                    <ks-button type="primary">Create app</ks-button>
                </template>
            </ks-empty-state>
        `,
    }),
}

export const WithLearnMore: Story = {
    render: () => ({
        components: {KsEmptyState, KsButton},
        template: `
            <ks-empty-state
                title="You have no policies yet!"
                description="Policies let you enforce rules on every flow in your tenant."
                learn-more="https://kestra.io/docs/enterprise/governance/policies"
            >
                <template #action>
                    <ks-button type="primary">Create policy</ks-button>
                </template>
            </ks-empty-state>
        `,
    }),
}

export const WithoutArtwork: Story = {
    render: () => ({
        components: {KsEmptyState},
        template:
            "<ks-empty-state title=\"Nothing here\" description=\"This variant has no artwork tile.\" />",
    }),
}

export const TitleOnly: Story = {
    render: () => ({
        components: {KsEmptyState},
        template: "<ks-empty-state title=\"Nothing to show.\" />",
    }),
}
