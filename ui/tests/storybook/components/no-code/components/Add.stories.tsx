import Add from "../../../../../src/components/no-code/components/Add.vue";
import {expect, fn, userEvent, within} from "storybook/test";
import {Meta, StoryObj} from "@storybook/vue3-vite";

const meta: Meta<typeof Add> = {
    title: "components/nocode/Add",
    component: Add,
};

export default meta;
type Story = StoryObj<typeof Add>;

export const Generic: Story = {
    args: {},
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await expect(canvas.getByRole("button", {name: "+ Add a new value"})).toBeVisible();
    },
};

export const NamedTarget: Story = {
    args: {to: "sla"},
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await expect(canvas.getByRole("button", {name: "+ Add to sla"})).toBeVisible();
    },
};

export const NounVariant: Story = {
    args: {what: "label", onAdd: fn()},
    async play({canvasElement, args}) {
        const canvas = within(canvasElement);
        const button = canvas.getByRole("button", {name: "+ Add a label"});
        await userEvent.click(button);
        await expect(args.onAdd).toHaveBeenCalledWith("label");
    },
};
