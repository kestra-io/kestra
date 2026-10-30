import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, spyOn, userEvent, waitFor, within} from "storybook/test"
import {KsNotification} from "@kestra-io/design-system"
import {apiUrl} from "override/utils/route"

import TriggerAvatar from "./TriggerAvatar.vue"

const flow = {
    namespace: "company.team",
    id: "slack-events",
    triggers: [{
        id: "slack",
        type: "io.kestra.plugin.slack.app.core.Trigger",
        key: "key/with spaces?",
    }],
}

const meta: Meta<typeof TriggerAvatar> = {
    title: "Components/Flows/TriggerAvatar",
    component: TriggerAvatar,
    args: {flow},
    beforeEach() {
        const writeText = spyOn(navigator.clipboard, "writeText").mockResolvedValue(undefined)
        return () => {
            writeText.mockRestore()
            KsNotification.closeAll()
        }
    },
    async play({canvasElement, args}) {
        const trigger = args.flow!.triggers![0]
        await userEvent.hover(within(canvasElement).getByRole("img", {name: trigger.type}))

        const body = within(canvasElement.ownerDocument.body)
        const copyButton = await body.findByRole("button", {name: "Copy URL"})
        await userEvent.click(copyButton)

        await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
            `${new URL(apiUrl(), window.location.origin).href}/executions/webhook/company.team/slack-events/key%2Fwith%20spaces%3F`,
        ))
        expect(navigator.clipboard.writeText).toHaveBeenCalledTimes(1)
        await expect(await body.findByText("Webhook link copied.")).toBeVisible()
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const Slack: Story = {}

export const SlackDark: Story = {
    parameters: {themes: {themeOverride: "dark"}},
}

export const LegacySlack: Story = {
    args: {
        flow: {
            ...flow,
            triggers: [{...flow.triggers[0], type: "io.kestra.plugin.slack.app.Trigger"}],
        },
    },
}

export const Webhook: Story = {
    args: {
        flow: {
            ...flow,
            triggers: [{...flow.triggers[0], type: "io.kestra.plugin.core.trigger.Webhook"}],
        },
    },
}

export const RedisList: Story = {
    args: {
        flow: {
            ...flow,
            triggers: [{...flow.triggers[0], type: "io.kestra.plugin.redis.list.Trigger", key: "mytriggerkey"}],
        },
    },
    async play({canvasElement, args}) {
        const trigger = args.flow!.triggers![0]
        await userEvent.hover(within(canvasElement).getByRole("img", {name: trigger.type}))

        const body = within(canvasElement.ownerDocument.body)
        const copyButton = await body.findByRole("button", {name: "Copy URL"})
        await userEvent.click(copyButton)

        expect(navigator.clipboard.writeText).not.toHaveBeenCalled()
    },
}
