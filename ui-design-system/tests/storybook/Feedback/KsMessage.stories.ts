import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect} from "storybook/test"
import {KsMessage} from "../../../src/components/Feedback/KsMessage"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta = {
    title: "Components/Feedback/KsMessage",
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsMessage is the Kestra design-system abstraction over `ElMessage` from Element Plus. " +
                    "It is a programmatic API — call `KsMessage(options)` or `KsMessage.success/warning/info/error(text)` " +
                    "to display a transient inline message at the top of the page.",
            },
        },
    },
}
export default meta
type Story = StoryObj

export const Success: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () => KsMessage.success("Flow saved successfully")
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="success" @click="show">Show success message</ks-button>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toBeTruthy()
        await userEvent.click(btn)
    },
}

export const Warning: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () => KsMessage.warning("Resource quota approaching limit")
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="warning" @click="show">Show warning message</ks-button>
            </div>
        `,
    }),
}

export const Error: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () => KsMessage.error("File type not allowed")
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="danger" @click="show">Show error message</ks-button>
            </div>
        `,
    }),
}

export const Info: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () => KsMessage.info("Next run is scheduled for 03:00 UTC")
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button @click="show">Show info message</ks-button>
            </div>
        `,
    }),
}

export const WithOptions: Story = {
    name: "With options object",
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () =>
                KsMessage({
                    message: "Login failed",
                    type: "error",
                    duration: 5000,
                    showClose: true,
                })
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="danger" @click="show">Show closable error (5 s)</ks-button>
            </div>
        `,
    }),
}

export const AllTypes: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const showAll = () => {
                KsMessage.success("Success!")
                setTimeout(() => KsMessage.warning("Warning!"), 400)
                setTimeout(() => KsMessage.info("Info!"), 800)
                setTimeout(() => KsMessage.error("Error!"), 1200)
            }
            return {showAll}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="showAll">Show all types</ks-button>
            </div>
        `,
    }),
}
