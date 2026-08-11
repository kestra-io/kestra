import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsTooltip from "../../../src/components/Feedback/KsTooltip.vue"

const meta: Meta<typeof KsTooltip> = {
    title: "Components/Feedback/KsTooltip",
    component: KsTooltip,
    tags: ["autodocs"],
    argTypes: {
        placement: {control: "select", options: ["top", "bottom", "left", "right", "top-start", "top-end", "bottom-start", "bottom-end"]},
        effect: {control: "select", options: ["light", "dark"]},
        trigger: {control: "select", options: ["hover", "click", "focus"]},
        disabled: {control: "boolean"},
        rawContent: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component: "KsTooltip is the Kestra design-system abstraction over `ElTooltip` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsTooltip>

export const Default: Story = {
    render: (args) => ({
        components: {KsTooltip, KsButton},
        setup() { return {args} },
        template: `
            <div style="padding:48px">
                <ks-tooltip v-bind="args">
                    <ks-button>Hover me</ks-button>
                </ks-tooltip>
            </div>
        `,
    }),
    args: {content: "This is a tooltip"},
}

export const Placements: Story = {
    render: () => ({
        components: {KsTooltip, KsButton},
        template: `
            <div style="padding:48px;display:flex;gap:16px;flex-wrap:wrap">
                <ks-tooltip content="Top" placement="top"><ks-button>Top</ks-button></ks-tooltip>
                <ks-tooltip content="Bottom" placement="bottom"><ks-button>Bottom</ks-button></ks-tooltip>
                <ks-tooltip content="Left" placement="left"><ks-button>Left</ks-button></ks-tooltip>
                <ks-tooltip content="Right" placement="right"><ks-button>Right</ks-button></ks-tooltip>
            </div>
        `,
    }),
}

export const WithContentSlot: Story = {
    render: () => ({
        components: {KsTooltip, KsButton},
        template: `
            <div style="padding:48px">
                <ks-tooltip effect="light" placement="top">
                    <ks-button type="primary">Rich content tooltip</ks-button>
                    <template #content>
                        <div><strong>Rich content</strong><br/>Can include <em>HTML</em></div>
                    </template>
                </ks-tooltip>
            </div>
        `,
    }),
}

/** Theme – dark and light */
export const Theme: Story = {
    render: () => ({
        components: {KsTooltip, KsButton},
        template: `
            <div style="padding:48px;display:flex;gap:16px">
                <ks-tooltip content="Dark theme" effect="dark" placement="top">
                    <ks-button>Dark</ks-button>
                </ks-tooltip>
                <ks-tooltip content="Light theme" effect="light" placement="top">
                    <ks-button>Light</ks-button>
                </ks-tooltip>
            </div>
        `,
    }),
}

/** HTML content via rawContent */
export const HTMLContent: Story = {
    render: () => ({
        components: {KsTooltip, KsButton},
        template: `
            <div style="padding:48px">
                <ks-tooltip
                    content="<strong>Bold</strong> and <em>italic</em> text"
                    :raw-content="true"
                    effect="light"
                    placement="top"
                >
                    <ks-button type="primary">HTML tooltip</ks-button>
                </ks-tooltip>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsTooltip, KsButton},
        template: `
            <div style="padding:48px">
                <ks-tooltip content="This won't show" :disabled="true">
                    <ks-button>Disabled tooltip</ks-button>
                </ks-tooltip>
            </div>
        `,
    }),
}
