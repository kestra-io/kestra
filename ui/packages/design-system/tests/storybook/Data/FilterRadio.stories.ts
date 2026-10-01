import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import FilterRadio from "../../../src/components/Data/KsDataTable/filter/layout/FilterRadio.vue"

const meta: Meta<typeof FilterRadio> = {
    title: "Components/Data/FilterRadio",
    component: FilterRadio,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "Single-select filter body. The first option is always the unfiltered one, labelled \"Default\" / \"Show default\" unless `allLabel` and `allDescription` override it.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof FilterRadio>

const KINDS = [
    {label: "Playground", value: "PLAYGROUND", description: "Executions triggered from Playground mode"},
    {label: "Loop", value: "LOOP", description: "Executions created by the Loop task"},
    {label: "Test", value: "TEST", description: "Executions triggered by Unit Tests"},
]

export const Default: Story = {
    args: {modelValue: "ALL", options: KINDS},
    play: async ({canvas}) => {
        await expect(canvas.getByText("Default")).toBeVisible()
        await expect(canvas.getByText("Show default")).toBeVisible()
    },
}

/**
 * The execution kind filter: the unfiltered option is the only one that lists standard executions,
 * so it is named after what it shows rather than left as a generic "Default".
 */
export const CustomAllOption: Story = {
    args: {
        modelValue: "ALL",
        options: KINDS,
        allLabel: "Standard",
        allDescription: "Standard execution outside of Playground, loop or test",
    },
    play: async ({canvas}) => {
        await expect(canvas.getByText("Standard")).toBeVisible()
        await expect(canvas.queryByText("Default")).toBeNull()
    },
}

export const OptionSelected: Story = {
    args: {
        modelValue: "PLAYGROUND",
        options: KINDS,
        allLabel: "Standard",
        allDescription: "Standard execution outside of Playground, loop or test",
    },
}
