import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import InputText from "./InputText.vue"

const meta = {
    title: "Components/NoCode/InputText",
    component: InputText,
    args: {
        modelValue: "Example value",
        placeholder: "Value",
    },
} satisfies Meta<typeof InputText>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {
    play: async ({canvasElement}) => {
        const wrapper = canvasElement.querySelector(".wrapper")
        if (!wrapper) throw new Error("Input wrapper not found")
        const style = getComputedStyle(wrapper)
        await expect(parseFloat(style.marginTop)).toBeGreaterThan(0)
        await expect(parseFloat(style.marginBottom)).toBeGreaterThan(0)
    },
}

export const WithoutMargin: Story = {
    args: {noMargin: true},
    play: async ({canvasElement}) => {
        const wrapper = canvasElement.querySelector(".wrapper")
        if (!wrapper) throw new Error("Input wrapper not found")
        const style = getComputedStyle(wrapper)
        await expect(style.marginTop).toBe("0px")
        await expect(style.marginBottom).toBe("0px")
    },
}
