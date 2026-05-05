import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect} from "storybook/test"
import {KsNotification} from "../../../src/components/Feedback/KsNotification"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta = {
    title: "Components/Feedback/KsNotification",
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsNotification is the Kestra design-system abstraction over `ElNotification` from Element Plus. " +
                    "Notifications appear in a corner of the screen (default: bottom-right) and are suited for " +
                    "asynchronous feedback such as save confirmations, job completions, or persistent errors.",
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
            const show = () =>
                KsNotification({
                    title: "Saved",
                    message: "Flow 'my-flow' was saved successfully.",
                    type: "success",
                    position: "bottom-right",
                })
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="success" @click="show">Show success notification</ks-button>
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
            const show = () =>
                KsNotification.warning({
                    title: "Warning",
                    message: "CPU usage is at 85% of the allocated quota.",
                    position: "bottom-right",
                })
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="warning" @click="show">Show warning notification</ks-button>
            </div>
        `,
    }),
}

export const PersistentError: Story = {
    name: "Persistent error (no auto-close)",
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () =>
                KsNotification.error({
                    title: "Task failed",
                    message: "Task 'fetch-data' failed with exit code 1. Check the logs for details.",
                    position: "bottom-right",
                    duration: 0,
                })
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="danger" @click="show">Show persistent error</ks-button>
                <p style="margin-top:8px;font-size:13px;color:#888">This notification stays until manually closed (duration: 0).</p>
            </div>
        `,
    }),
}

export const Info: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () =>
                KsNotification.info({
                    title: "Scheduled run",
                    message: "Next execution is scheduled for today at 03:00 UTC.",
                    position: "bottom-right",
                })
            return {show}
        },
        template: `
            <div style="padding:24px">
                <ks-button @click="show">Show info notification</ks-button>
            </div>
        `,
    }),
}

export const AllTypes: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const showAll = () => {
                KsNotification.success({title: "Success", message: "Flow saved", position: "bottom-right"})
                setTimeout(() => KsNotification.warning({title: "Warning", message: "Quota at 85%", position: "bottom-right"}), 400)
                setTimeout(() => KsNotification.info({title: "Info", message: "Scheduled", position: "bottom-right"}), 800)
                setTimeout(() => KsNotification.error({title: "Error", message: "Task failed", position: "bottom-right", duration: 0}), 1200)
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

export const CloseAll: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const show = () => {
                for (let i = 0; i < 3; i++) {
                    KsNotification.info({title: `Notification ${i + 1}`, message: "Click close all to dismiss", position: "bottom-right", duration: 0})
                }
            }
            const closeAll = () => KsNotification.closeAll()
            return {show, closeAll}
        },
        template: `
            <div style="padding:24px;display:flex;gap:12px">
                <ks-button @click="show">Open 3 notifications</ks-button>
                <ks-button type="danger" @click="closeAll">Close all</ks-button>
            </div>
        `,
    }),
}
