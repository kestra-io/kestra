import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsSegmentedProgress from "../../../src/components/Data/KsSegmentedProgress.vue"

const meta: Meta<typeof KsSegmentedProgress> = {
    title: "Components/Data/KsSegmentedProgress",
    component: KsSegmentedProgress,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsSegmentedProgress renders a stacked progress bar made of independently colored segments, each sized proportionally to `total`. Typical use case: showing how many items of a batch have reached each of several terminal states (success, failed, cancelled...). Hover a segment to see its tooltip. The default slot receives `{value, total}` (`value` is the sum of all segment values) to let the caller format the trailing label.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsSegmentedProgress>

export const Default: Story = {
    render: (args) => ({
        components: {KsSegmentedProgress},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-segmented-progress v-bind=\"args\" /></div>",
    }),
    args: {
        total: 10,
        segments: [
            {key: "SUCCESS", value: 6, color: "var(--ks-status-success)", tooltip: "6 SUCCESS"},
            {key: "FAILED", value: 1, color: "var(--ks-status-error)", tooltip: "1 FAILED"},
        ],
    },
}

export const WithLabel: Story = {
    render: () => ({
        components: {KsSegmentedProgress},
        template: `
            <div style="padding:24px;max-width:320px">
                <ks-segmented-progress
                    :total="10"
                    :segments="[
                        {key: 'SUCCESS', value: 6, color: 'var(--ks-status-success)', tooltip: '6 SUCCESS'},
                        {key: 'FAILED', value: 1, color: 'var(--ks-status-error)', tooltip: '1 FAILED'},
                    ]"
                >
                    <template #default="{value, total}">{{ value }} / {{ total }}</template>
                </ks-segmented-progress>
            </div>
        `,
    }),
    parameters: {
        docs: {description: {story: "The default slot is scoped with `{value, total}` so callers can render any label format next to the bar."}},
    },
}

export const ManyStates: Story = {
    render: () => ({
        components: {KsSegmentedProgress},
        template: `
            <div style="padding:24px;max-width:320px">
                <ks-segmented-progress
                    :total="20"
                    :segments="[
                        {key: 'SUCCESS', value: 10, color: 'var(--ks-status-success)', tooltip: '10 SUCCESS'},
                        {key: 'WARNING', value: 3, color: 'var(--ks-status-warning)', tooltip: '3 WARNING'},
                        {key: 'FAILED', value: 2, color: 'var(--ks-status-error)', tooltip: '2 FAILED'},
                    ]"
                >
                    <template #default="{value, total}">{{ value }} / {{ total }}</template>
                </ks-segmented-progress>
            </div>
        `,
    }),
}

export const Empty: Story = {
    render: () => ({
        components: {KsSegmentedProgress},
        template: `
            <div style="padding:24px;max-width:320px">
                <ks-segmented-progress :total="10" :segments="[]">
                    <template #default="{value, total}">{{ value }} / {{ total }}</template>
                </ks-segmented-progress>
            </div>
        `,
    }),
    parameters: {
        docs: {description: {story: "With no segments yet reached (e.g. a Loop task that just started), the track renders empty but the label still shows `0 / total`."}},
    },
}
