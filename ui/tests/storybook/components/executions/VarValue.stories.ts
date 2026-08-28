import type {Meta, StoryObj} from "@storybook/vue3"
import {expect, waitFor, within} from "storybook/test"

import VarValue from "../../../../src/components/executions/VarValue.vue"

/** Object-shaped values go through an inline Monaco editor; the empty ones must not. */
const meta: Meta<typeof VarValue> = {
    title: "Components/Executions/VarValue",
    component: VarValue,
}

export default meta
type Story = StoryObj<typeof meta>;

/** An empty object renders as `{}`, with no editor. */
export const EmptyObject: Story = {
    args: {value: {}},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)

        await waitFor(() => expect(canvas.getByText("{}")).toBeTruthy())
        expect(canvasElement.querySelector(".complex-value-editor")).toBeNull()
    },
}

/** Same for an empty array, and for one arriving as a JSON string. */
export const EmptyArray: Story = {
    args: {value: []},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)

        await waitFor(() => expect(canvas.getByText("[]")).toBeTruthy())
        expect(canvasElement.querySelector(".complex-value-editor")).toBeNull()
    },
}

export const EmptyObjectAsString: Story = {
    args: {value: "{}"},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)

        await waitFor(() => expect(canvas.getByText("{}")).toBeTruthy())
        expect(canvasElement.querySelector(".complex-value-editor")).toBeNull()
    },
}

/** A populated object still gets the editor. Monaco loads async, hence the timeout. */
export const PopulatedObject: Story = {
    args: {value: {code: 200}},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        await waitFor(() => expect(canvasElement.querySelector(".complex-value-editor")).not.toBeNull(), {timeout: 15000})
    },
}

/** A scalar is printed as-is. */
export const Scalar: Story = {
    args: {value: 42},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)

        await waitFor(() => expect(canvas.getByText("42")).toBeTruthy())
    },
}
