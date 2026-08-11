import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsText from "../../../src/components/Basic/KsText.vue"

const meta: Meta<typeof KsText> = {
    title: "Components/Basic/KsText",
    component: KsText,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["", "primary", "success", "warning", "danger", "info"]},
        size: {control: "select", options: ["small", "default", "large"]},
        truncated: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsText is the Kestra design-system abstraction over `ElText` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsText>

export const Default: Story = {
    render: (args) => ({
        components: {KsText},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-text v-bind=\"args\">Sample text content</ks-text></div>",
    }),
    args: {type: ""},
}

export const Types: Story = {
    render: () => ({
        components: {KsText},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:8px">
                <ks-text>Default</ks-text>
                <ks-text type="primary">Primary</ks-text>
                <ks-text type="success">Success</ks-text>
                <ks-text type="info">Info</ks-text>
                <ks-text type="warning">Warning</ks-text>
                <ks-text type="danger">Danger</ks-text>
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsText},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:8px">
                <ks-text size="large">Large text</ks-text>
                <ks-text size="default">Default text</ks-text>
                <ks-text size="small">Small text</ks-text>
            </div>
        `,
    }),
}

/** Line clamp – multiline truncation */
export const LineClamp: Story = {
    render: () => ({
        components: {KsText},
        template: `
            <div style="padding:24px;width:300px;display:flex;flex-direction:column;gap:12px">
                <ks-text :line-clamp="2">
                    This text will be clamped to two lines. It is long enough to demonstrate the line-clamp behavior when the content exceeds the allotted space.
                </ks-text>
            </div>
        `,
    }),
}

/** Override – render as different HTML elements */
export const Override: Story = {
    render: () => ({
        components: {KsText},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:8px">
                <ks-text tag="p">Paragraph &lt;p&gt;</ks-text>
                <ks-text tag="b">Bold &lt;b&gt;</ks-text>
                <ks-text tag="i">Italic &lt;i&gt;</ks-text>
                <ks-text tag="sub">Subscript &lt;sub&gt;</ks-text>
                <ks-text tag="sup">Superscript &lt;sup&gt;</ks-text>
            </div>
        `,
    }),
}

export const Truncated: Story = {
    render: () => ({
        components: {KsText},
        template: `
            <div style="padding:24px;width:200px">
                <ks-text truncated>This is a very long text that will be truncated when it exceeds the container width</ks-text>
            </div>
        `,
    }),
}
