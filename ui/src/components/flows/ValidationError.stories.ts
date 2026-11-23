import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, expect, fn, userEvent} from "storybook/test"
import ValidationError from "./ValidationError.vue"

const errors = ["tasks[0].message: must not be null", "id: must match \"^[a-zA-Z0-9][a-zA-Z0-9_-]*\""]

const meta: Meta<typeof ValidationError> = {
    title: "Components/Flows/ValidationError",
    component: ValidationError,
    args: {onActivate: fn()},
    render: (args) => ({
        components: {ValidationError},
        setup() {
            return {args}
        },
        template: "<ValidationError v-bind=\"args\" />",
    }),
}
export default meta
type Story = StoryObj<typeof ValidationError>

export const Valid: Story = {
    async play({canvasElement}) {
        await expect(await within(canvasElement).findByText("Valid")).toBeVisible()
    },
}

export const Errors: Story = {
    args: {errors},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(await canvas.findByText("2 Error(s)")).toBeVisible()
        await expect(canvas.queryByRole("button")).toBeNull()
    },
}

export const ErrorsWithNavigation: Story = {
    args: {errors, activatable: true},
    async play({canvasElement, args}) {
        const canvas = within(canvasElement)
        const button = await canvas.findByRole("button", {name: "2 error(s), go to the next error"})
        await expect(button).toHaveTextContent("2 Error(s)")

        await userEvent.click(button)
        await userEvent.click(button)

        await expect(args.onActivate).toHaveBeenCalledTimes(2)
    },
}

export const ErrorsWithNavigationIconOnly: Story = {
    args: {errors, activatable: true, iconOnly: true},
    async play({canvasElement}) {
        await expect(await within(canvasElement).findByRole("button", {name: "2 error(s), go to the next error"})).toBeVisible()
    },
}

export const Warnings: Story = {
    args: {warnings: ["tasks[0].type is deprecated."]},
    async play({canvasElement}) {
        await expect(await within(canvasElement).findByText("Warning(s) detected.")).toBeVisible()
    },
}
