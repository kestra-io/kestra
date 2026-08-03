import type {Meta, StoryObj} from "@storybook/vue3"
import {userEvent, within, waitFor, expect} from "storybook/test"
import LogDisplaySettings from "../../../../src/components/logs/LogDisplaySettings.vue"

const meta: Meta<typeof LogDisplaySettings> = {
    title: "Components/Logs/LogDisplaySettings",
    component: LogDisplaySettings,
    parameters: {layout: "centered"},
}

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}

export const Opened: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await userEvent.click(btn)
        await waitFor(() =>
            expect(canvasElement.ownerDocument.body.querySelector(".log-display-settings")).toBeTruthy()
        )
    },
}
