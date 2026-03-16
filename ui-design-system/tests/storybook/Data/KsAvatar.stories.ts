import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsAvatar from "../../../src/components/Data/KsAvatar.vue"

const meta: Meta<typeof KsAvatar> = {
    title: "Components/Data/KsAvatar",
    component: KsAvatar,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["large", "default", "small", 64, 32]},
        shape: {control: "select", options: ["circle", "square"]},
        fit: {control: "select", options: ["fill", "contain", "cover", "none", "scale-down"]},
    },
    parameters: {
        docs: {description: {component: "KsAvatar is the Kestra design-system abstraction over `ElAvatar` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsAvatar>

export const Default: Story = {
    render: (args) => ({
        components: {KsAvatar},
        setup() { return {args} },
        template: `<div style="padding:24px"><ks-avatar v-bind="args" /></div>`,
    }),
    args: {src: "https://cube.elemecdn.com/3/7c/3ea0eb2965f53f9570b3a1a9891c3png.png"},
}

export const Shapes: Story = {
    render: () => ({
        components: {KsAvatar},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-avatar shape="circle" src="https://cube.elemecdn.com/3/7c/3ea0eb2965f53f9570b3a1a9891c3png.png" />
                <ks-avatar shape="square" src="https://cube.elemecdn.com/3/7c/3ea0eb2965f53f9570b3a1a9891c3png.png" />
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsAvatar},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-avatar size="large" src="https://cube.elemecdn.com/3/7c/3ea0eb2965f53f9570b3a1a9891c3png.png" />
                <ks-avatar size="default" src="https://cube.elemecdn.com/3/7c/3ea0eb2965f53f9570b3a1a9891c3png.png" />
                <ks-avatar size="small" src="https://cube.elemecdn.com/3/7c/3ea0eb2965f53f9570b3a1a9891c3png.png" />
            </div>
        `,
    }),
}

export const WithFallback: Story = {
    render: () => ({
        components: {KsAvatar},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-avatar>JD</ks-avatar>
            </div>
        `,
    }),
}
