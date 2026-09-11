import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect, waitFor} from "storybook/test"
import {ref} from "vue"

import UndoToast from "../../../../src/components/no-code/blocks/UndoToast.vue"

const meta: Meta<typeof UndoToast> = {
    title: "No-code/UndoToast",
    component: UndoToast,
}

export default meta
type Story = StoryObj<typeof UndoToast>

export const Shown: Story = {
    render: () => ({
        components: {UndoToast},
        setup() {
            return {state: {label: "publish deleted"}}
        },
        template: `<div style="position:relative;height:160px"><UndoToast :state="state" /></div>`,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("publish deleted")).toBeVisible()
        await expect(canvasElement.querySelector("[data-test='undo-toast-button']")).toBeVisible()
    },
}

export const Hidden: Story = {
    render: () => ({
        components: {UndoToast},
        template: `<div style="position:relative;height:160px"><UndoToast :state="null" /></div>`,
    }),
    play: async ({canvasElement}) => {
        await expect(canvasElement.querySelector("[data-test='undo-toast-button']")).toBeNull()
    },
}

export const LongLabel: Story = {
    render: () => ({
        components: {UndoToast},
        setup() {
            return {state: {label: "a_task_with_a_deliberately_very_long_identifier_deleted"}}
        },
        template: `<div style="position:relative;height:160px"><UndoToast :state="state" /></div>`,
    }),
    play: async ({canvasElement}) => {
        await expect(canvasElement.querySelector("[data-test='undo-toast-button']")).toBeVisible()
    },
}

export const EmitsUndoOnce: Story = {
    render: () => ({
        components: {UndoToast},
        setup() {
            const undone = ref(0)
            const state = ref<{label: string} | null>({label: "publish deleted"})
            return {
                state,
                undone,
                onUndo: () => {
                    undone.value += 1
                    state.value = null
                },
            }
        },
        template: `
            <div style="position:relative;height:160px">
                <UndoToast :state="state" @undo="onUndo" />
                <span data-test="undo-count">{{ undone }}</span>
            </div>
        `,
    }),
    play: async ({canvasElement}) => {
        await userEvent.click(canvasElement.querySelector("[data-test='undo-toast-button']") as HTMLElement)
        await expect(canvasElement.querySelector("[data-test='undo-count']")).toHaveTextContent("1")
        // The toast is the only affordance, so it has to go once used — after its leave transition.
        await waitFor(() =>
            expect(canvasElement.querySelector("[data-test='undo-toast-button']")).toBeNull(),
        )
    },
}
