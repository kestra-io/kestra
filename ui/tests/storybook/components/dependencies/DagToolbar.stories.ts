import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, within} from "storybook/test"
import DagToolbar from "../../../../src/components/dependencies/components/dag/DagToolbar.vue"

const meta: Meta<typeof DagToolbar> = {
    title: "Components/Dependencies/DagToolbar",
    component: DagToolbar,
    args: {
        nodes: [],
        groupFields: [],
        groupChips: [],
        layoutMode: "dag",
        groupField: "",
    },
    render: (args) => ({
        components: {DagToolbar},
        setup() {
            return {args}
        },
        template: "<DagToolbar v-bind=\"args\" />",
    }),
}

export default meta

type Story = StoryObj<typeof DagToolbar>

export const RelationLegend: Story = {
    args: {relationKinds: new Set(["PRODUCES", "UPSTREAM_OF"])},
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(canvas.getByText("Produces")).toBeVisible()
        await expect(canvas.getByText("Upstream of")).toBeVisible()
        await expect(canvas.queryByText("Consumed by")).toBeNull()
    },
}

export const NoRelationKind: Story = {
    args: {relationKinds: new Set()},
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(canvas.queryByText("Produces")).toBeNull()
    },
}
