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
        setup() {
            return () => (
                <div style={{padding: "24px", maxWidth: "400px"}}>
                    <KsTimeline>
                        <KsTimelineItem timestamp="2024-01-15 09:00" placement="top" type="success">
                            Flow execution started
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="2024-01-15 09:05" placement="top">
                            Task 1 completed
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="2024-01-15 09:10" placement="top" type="primary">
                            Task 2 running
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="2024-01-15 09:15" placement="top" type="danger">
                            Flow execution failed
                        </KsTimelineItem>
                    </KsTimeline>
                </div>
            )
        },
    }),
}

/** Custom node – size, icon, hollow */
export const CustomNode: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style={{padding: "24px", maxWidth: "400px"}}>
                    <KsTimeline>
                        <KsTimelineItem timestamp="Queued" color="#909399" size="normal" hollow>
                            Execution queued
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="Running" color="#409eff" size="large">
                            Tasks executing
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="Done" color="#67c23a" size="large">
                            Completed successfully
                        </KsTimelineItem>
                    </KsTimeline>
                </div>
            )
        },
    }),
}

/** Custom timestamp placement – above the content */
export const TimestampPlacement: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style={{padding: "24px", maxWidth: "400px"}}>
                    <KsTimeline>
                        <KsTimelineItem timestamp="2024-01-15 09:00" placement="top">
                            Flow started
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="2024-01-15 09:05">
                            Task 1 completed (timestamp below)
                        </KsTimelineItem>
                        <KsTimelineItem timestamp="2024-01-15 09:12" placement="top">
                            Flow finished
                        </KsTimelineItem>
                    </KsTimeline>
                </div>
            )
        },
    }),
}

/** Hide timestamp */
export const HideTimestamp: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style={{padding: "24px", maxWidth: "400px"}}>
                    <KsTimeline>
                        <KsTimelineItem timestamp="09:00" color="#67c23a">Started</KsTimelineItem>
                        <KsTimelineItem hideTimestamp color="#409eff">Running (no timestamp)</KsTimelineItem>
                        <KsTimelineItem timestamp="09:15" color="#f56c6c">Failed</KsTimelineItem>
                    </KsTimeline>
                </div>
            )
        },
    }),
}

/** hollow – node ring with transparent fill */
export const Hollow: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style={{padding: "24px", maxWidth: "400px"}}>
                    <KsTimeline>
                        <KsTimelineItem type="primary" timestamp="Step 1" hollow>
                            Hollow node
                        </KsTimelineItem>
                        <KsTimelineItem type="primary" timestamp="Step 2">
                            Filled node
                        </KsTimelineItem>
                    </KsTimeline>
                </div>
            )
        },
    }),
}

export const WithColors: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style={{padding: "24px", maxWidth: "400px"}}>
                    <KsTimeline>
                        <KsTimelineItem color="#00bfff" timestamp="Step 1">
                            Initialize environment
                        </KsTimelineItem>
                        <KsTimelineItem color="#7cfc00" timestamp="Step 2">
                            Run tests
                        </KsTimelineItem>
                        <KsTimelineItem color="#ffa500" timestamp="Step 3" size="large">
                            Deploy to staging
                        </KsTimelineItem>
                    </KsTimeline>
                </div>
            )
        },
    }),
}
