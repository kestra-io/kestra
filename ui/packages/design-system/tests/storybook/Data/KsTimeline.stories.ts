import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTimeline from "../../../src/components/Data/KsTimeline/KsTimeline.vue"
import KsTimelineItem from "../../../src/components/Data/KsTimeline/KsTimelineItem.vue"

const meta: Meta<typeof KsTimeline> = {
    title: "Components/Data/KsTimeline",
    component: KsTimeline,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsTimeline is the Kestra design-system abstraction over `ElTimeline` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTimeline>

export const Default: Story = {
    render: () => ({
        components: {KsTimeline, KsTimelineItem},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-timeline>
                    <ks-timeline-item timestamp="2024-01-15 09:00" placement="top" type="success">
                        Flow execution started
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="2024-01-15 09:05" placement="top">
                        Task 1 completed
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="2024-01-15 09:10" placement="top" type="primary">
                        Task 2 running
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="2024-01-15 09:15" placement="top" type="danger">
                        Flow execution failed
                    </ks-timeline-item>
                </ks-timeline>
            </div>
        `,
    }),
}

/** Custom node – size, icon, hollow */
export const CustomNode: Story = {
    render: () => ({
        components: {KsTimeline, KsTimelineItem},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-timeline>
                    <ks-timeline-item timestamp="Queued" color="#909399" size="normal" hollow>
                        Execution queued
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="Running" color="#409eff" size="large">
                        Tasks executing
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="Done" color="#67c23a" size="large">
                        Completed successfully
                    </ks-timeline-item>
                </ks-timeline>
            </div>
        `,
    }),
}

/** Custom timestamp placement – above the content */
export const TimestampPlacement: Story = {
    render: () => ({
        components: {KsTimeline, KsTimelineItem},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-timeline>
                    <ks-timeline-item timestamp="2024-01-15 09:00" placement="top">
                        Flow started
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="2024-01-15 09:05">
                        Task 1 completed (timestamp below)
                    </ks-timeline-item>
                    <ks-timeline-item timestamp="2024-01-15 09:12" placement="top">
                        Flow finished
                    </ks-timeline-item>
                </ks-timeline>
            </div>
        `,
    }),
}

/** Hide timestamp */
export const HideTimestamp: Story = {
    render: () => ({
        components: {KsTimeline, KsTimelineItem},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-timeline>
                    <ks-timeline-item timestamp="09:00" color="#67c23a">Started</ks-timeline-item>
                    <ks-timeline-item hide-timestamp color="#409eff">Running (no timestamp)</ks-timeline-item>
                    <ks-timeline-item timestamp="09:15" color="#f56c6c">Failed</ks-timeline-item>
                </ks-timeline>
            </div>
        `,
    }),
}

export const WithColors: Story = {
    render: () => ({
        components: {KsTimeline, KsTimelineItem},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-timeline>
                    <ks-timeline-item color="#00bfff" timestamp="Step 1">
                        Initialize environment
                    </ks-timeline-item>
                    <ks-timeline-item color="#7cfc00" timestamp="Step 2">
                        Run tests
                    </ks-timeline-item>
                    <ks-timeline-item color="#ffa500" timestamp="Step 3" size="large">
                        Deploy to staging
                    </ks-timeline-item>
                </ks-timeline>
            </div>
        `,
    }),
}
