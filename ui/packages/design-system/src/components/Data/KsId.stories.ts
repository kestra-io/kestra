import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import KsId from "./KsId.vue"

const meta: Meta<typeof KsId> = {
    title: "Components/Data/KsId",
    component: KsId,
    tags: ["autodocs"],
    argTypes: {
        value: {control: "text"},
        shrink: {control: "boolean"},
        size: {control: "number"},
    },
    parameters: {
        docs: {description: {component: "KsId displays a (typically UUID-like) identifier, optionally truncated with a tooltip showing the full value on hover."}},
    },
}
export default meta
type Story = StoryObj<typeof KsId>

export const Default: Story = {
    render: (args) => ({
        components: {KsId},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-id v-bind=\"args\" /></div>",
    }),
    args: {value: "a1b2c3d4-e5f6-7890-abcd-ef1234567890", shrink: true, size: 8, placement: "top"},
}

export const Shrink: Story = {
    render: () => ({
        components: {KsId},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-id value="a1b2c3d4-e5f6-7890-abcd-ef1234567890" :shrink="false" />
                <ks-id value="a1b2c3d4-e5f6-7890-abcd-ef1234567890" :shrink="true" :size="12" />
                <ks-id value="a1b2c3d4-e5f6-7890-abcd-ef1234567890" :shrink="true" />
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

export const Clickable: Story = {
    render: () => ({
        components: {KsId},
        setup() {
            return {
                handleClick: () => alert("ID clicked!"),
            }
        },
        template: `
            <div style="padding:24px">
                <ks-id value="a1b2c3d4-e5f6-7890-abcd-ef1234567890" @click="handleClick" />
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

export const ShortValue: Story = {
    render: () => ({
        components: {KsId},
        template: `
            <div style="padding:24px">
                <ks-id value="abc123" />
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

export const Empty: Story = {
    render: () => ({
        components: {KsId},
        template: `
            <div style="padding:24px">
                <ks-id />
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

/** Colour: `--ks-id-color` replaces the link colour, set on the id or an ancestor such as a link wrapping it */
export const Color: Story = {
    render: () => ({
        components: {KsId},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-id value="a1b2c3d4-e5f6-7890" :shrink="false" data-test="default" />
                <span style="--ks-id-color:var(--ks-text-primary)"><ks-id value="a1b2c3d4-e5f6-7890" :shrink="false" data-test="primary" /></span>
                <span data-test="reference" style="color:var(--ks-text-primary)">reference</span>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
    async play({canvasElement}) {
        const color = (test: string) => getComputedStyle(canvasElement.querySelector(`[data-test="${test}"]`)!).color
        await expect(color("primary")).toBe(color("reference"))
        await expect(color("default")).not.toBe(color("reference"))
    },
}
