import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, expect} from "storybook/test"

import BlockEmptyDrop from "../../../../src/components/no-code/blocks/BlockEmptyDrop.vue"

const meta: Meta<typeof BlockEmptyDrop> = {
    title: "No-code/BlockEmptyDrop",
    component: BlockEmptyDrop,
}

export default meta
type Story = StoryObj<typeof BlockEmptyDrop>

export const Empty: Story = {
    render: () => ({
        components: {BlockEmptyDrop},
        template: `<BlockEmptyDrop variant="empty" label="task" hint="or press / to search tasks" />`,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("Click to add task")).toBeVisible()
        await expect(canvas.getByText("or press / to search tasks")).toBeVisible()
    },
}

export const Inline: Story = {
    render: () => ({
        components: {BlockEmptyDrop},
        template: `<BlockEmptyDrop variant="inline" label="task" />`,
    }),
    play: async ({canvasElement}) => {
        await expect(within(canvasElement).getByText("Add task")).toBeVisible()
    },
}

export const AllowedDropTarget: Story = {
    name: "Drop target — allowed",
    render: () => ({
        components: {BlockEmptyDrop},
        template: `<BlockEmptyDrop variant="inline" label="task" dropState="allowed" />`,
    }),
    play: async ({canvasElement}) => {
        const button = canvasElement.querySelector("[data-test], button") as HTMLElement
        await expect(button.className).toContain("block-empty-drop--drop-allowed")
    },
}

export const ForbiddenDropTarget: Story = {
    name: "Drop target — forbidden",
    render: () => ({
        components: {BlockEmptyDrop},
        template: `<BlockEmptyDrop variant="inline" label="task" dropState="forbidden" />`,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("Can't drop here")).toBeVisible()
        const button = canvasElement.querySelector("button") as HTMLElement
        await expect(button.className).toContain("block-empty-drop--drop-forbidden")
    },
}
