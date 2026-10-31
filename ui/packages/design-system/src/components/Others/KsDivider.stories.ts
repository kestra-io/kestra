import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsDivider from "./KsDivider.vue"

const meta: Meta<typeof KsDivider> = {
    title: "Components/Others/KsDivider",
    component: KsDivider,
    tags: ["autodocs"],
    argTypes: {
        direction: {control: "select", options: ["horizontal", "vertical"]},
        contentPosition: {control: "select", options: ["left", "center", "right"]},
        borderStyle: {control: "select", options: ["solid", "dashed", "dotted"]},
    },
    parameters: {
        docs: {description: {component: "KsDivider is the Kestra design-system abstraction over `ElDivider` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsDivider>

export const Default: Story = {
    render: (args) => ({
        components: {KsDivider},
        setup() { return {args} },
        template: `
            <div style="padding:24px">
                <p>Content above the divider</p>
                <ks-divider v-bind="args">Section</ks-divider>
                <p>Content below the divider</p>
            </div>
        `,
    }),
    args: {direction: "horizontal", contentPosition: "center", borderStyle: "solid"},
}

export const WithText: Story = {
    render: () => ({
        components: {KsDivider},
        template: `
            <div style="padding:24px">
                <p>Section One</p>
                <ks-divider content-position="left">Left</ks-divider>
                <p>Section Two</p>
                <ks-divider>Center</ks-divider>
                <p>Section Three</p>
                <ks-divider content-position="right">Right</ks-divider>
                <p>Section Four</p>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

/** Dashed border style */
export const Dashed: Story = {
    render: () => ({
        components: {KsDivider},
        template: `
            <div style="padding:24px">
                <p>Section above</p>
                <ks-divider border-style="dashed" />
                <p>Section below (dashed)</p>
                <ks-divider border-style="dotted" />
                <p>Section below (dotted)</p>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

export const Vertical: Story = {
    render: () => ({
        components: {KsDivider},
        template: `
            <div style="padding:24px;display:flex;align-items:center">
                <span>Item A</span>
                <ks-divider direction="vertical" />
                <span>Item B</span>
                <ks-divider direction="vertical" />
                <span>Item C</span>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}
