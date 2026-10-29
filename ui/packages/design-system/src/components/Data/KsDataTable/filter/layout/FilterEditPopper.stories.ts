import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, fn, userEvent, waitFor, within} from "storybook/test"
import FilterEditPopper from "./FilterEditPopper.vue"
import {Comparators, type FilterKeyConfig} from "../utils/filterTypes"
import locale from "../../KsFilter.locale"

const kindKey: FilterKeyConfig = {
    key: "kind",
    label: locale.en.filter.kind.label,
    valueType: "radio",
    comparators: [Comparators.EQUALS],
    allLabel: locale.en.filter.execution_kind.standard,
    allDescription: locale.en.filter.execution_kind.standard_description,
    valueProvider: async () => ["playground", "loop", "test"].map(kind => ({
        value: kind.toUpperCase(),
        label: locale.en.filter.execution_kind[kind as "playground" | "loop" | "test"],
    })),
}

const meta = {
    title: "Components/Data/KsFilter/Radio editor",
    component: FilterEditPopper,
    args: {
        filterKey: kindKey,
        filter: {
            id: "kind",
            key: "kind",
            keyLabel: kindKey.label,
            comparator: Comparators.EQUALS,
            comparatorLabel: "",
            value: "",
            valueLabel: "",
        },
        onUpdate: fn(),
    },
} satisfies Meta<typeof FilterEditPopper>

export default meta
type Story = StoryObj<typeof meta>

export const Standard: Story = {
    play: async ({canvasElement, args}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText(kindKey.allDescription!)).toBeVisible()
        await expect(canvas.queryByText("Normal")).not.toBeInTheDocument()
        await expect(canvas.queryByText("Default")).not.toBeInTheDocument()

        await userEvent.click(canvas.getByText("Playground"))
        await waitFor(() => expect(args.onUpdate).toHaveBeenLastCalledWith(expect.objectContaining({value: "PLAYGROUND"})))
        await userEvent.click(canvas.getByText("Standard", {exact: true}))
        await waitFor(() => expect(args.onUpdate).toHaveBeenLastCalledWith(expect.objectContaining({value: ""})))
    },
}

export const LegacyNormal: Story = {
    args: {filter: {...meta.args.filter, value: "NORMAL", valueLabel: "NORMAL"}},
    play: async ({canvasElement}) => {
        await expect(canvasElement.querySelector("[data-test=\"filter-radio-default\"] input")).toBeChecked()
    },
}

export const ExistingPlayground: Story = {
    args: {filter: {...meta.args.filter, value: "PLAYGROUND", valueLabel: "PLAYGROUND"}},
    play: async ({canvasElement}) => {
        await waitFor(() => expect(canvasElement.querySelector("[data-test=\"filter-radio-PLAYGROUND\"] input")).toBeChecked())
    },
}

export const Hierarchy: Story = {
    args: {
        filterKey: {
            key: "childFilter",
            label: locale.en.filter.childFilter.label,
            valueType: "radio",
            comparators: [Comparators.EQUALS],
            valueProvider: async () => [{label: "Parent", value: "MAIN"}],
        },
    },
}

export const Dark: Story = {
    globals: {theme: "dark"},
}
