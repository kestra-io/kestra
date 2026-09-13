import type {Meta, StoryObj} from "@storybook/vue3"
import {userEvent, within, expect, waitFor} from "storybook/test"
import LogValueActions from "../../../../src/components/logs/LogValueActions.vue"

const meta: Meta<typeof LogValueActions> = {
    title: "Components/Logs/LogValueActions",
    component: LogValueActions,
    parameters: {layout: "centered"},
    argTypes: {
        field: {control: "text"},
        value: {control: "text"},
        filterable: {control: "boolean"},
    },
}

export default meta
type Story = StoryObj<typeof meta>

const Wrap = (args: any, slotContent: string) => ({
    components: {LogValueActions},
    setup: () => () => (
        <LogValueActions {...args}>
            <span style="font-family: var(--ks-font-family-mono); font-size: 13px">
                {slotContent}
            </span>
        </LogValueActions>
    ),
})

export const FilterableWithLink: Story = {
    args: {
        field: "flowId",
        value: "daily-etl",
        filterable: true,
        to: {name: "flows/update", params: {namespace: "company.data", id: "daily-etl"}},
    },
    render: (args) => Wrap(args, "daily-etl"),
}

export const FilterableNoLink: Story = {
    args: {
        field: "taskId",
        value: "extract_data",
        filterable: true,
    },
    render: (args) => Wrap(args, "extract_data"),
}

export const CopyOnly: Story = {
    args: {
        field: "thread",
        value: "worker-thread-4",
        filterable: false,
    },
    render: (args) => Wrap(args, "worker-thread-4"),
}

export const OpensOnClick: Story = {
    args: {
        field: "flowId",
        value: "my-flow",
        filterable: true,
    },
    render: (args) => Wrap(args, "my-flow"),
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)
        const trigger = canvas.getByRole("button")
        await userEvent.click(trigger)
        await waitFor(() => expect(canvasElement.ownerDocument.body.querySelector(".log-value-action")).toBeTruthy())
    },
}
