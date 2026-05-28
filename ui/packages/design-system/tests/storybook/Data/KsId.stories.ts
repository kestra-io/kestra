import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsId from "../../../src/components/Data/KsId.vue"

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
    args: {value: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"},
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
}
