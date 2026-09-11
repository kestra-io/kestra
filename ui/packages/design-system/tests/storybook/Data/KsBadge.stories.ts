import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsBadge from "../../../src/components/Data/KsBadge.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsBadge> = {
    title: "Components/Data/KsBadge",
    component: KsBadge,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["primary", "success", "warning", "danger", "info"]},
        inline: {control: "boolean"},
        isDot: {control: "boolean"},
        hidden: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component: [
                    "KsBadge is the Kestra design-system abstraction over `ElBadge` from Element Plus.",
                    "",
                    "It has two modes. By default it **overlays** a wrapped child — pass the child in the default",
                    "slot and the pill is positioned over its top-right corner. With `inline`, it renders in the",
                    "normal flow as a standalone counter, which is what you want next to a tab or menu label.",
                ].join("\n"),
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsBadge>

export const Default: Story = {
    render: (args) => ({
        components: {KsBadge, KsButton},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-badge v-bind=\"args\"><ks-button>Messages</ks-button></ks-badge></div>",
    }),
    args: {value: 5},
}

export const Types: Story = {
    render: () => ({
        components: {KsBadge, KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:24px">
                <ks-badge :value="3" type="primary"><ks-button>Primary</ks-button></ks-badge>
                <ks-badge :value="5" type="success"><ks-button>Success</ks-button></ks-badge>
                <ks-badge :value="2" type="danger"><ks-button>Danger</ks-button></ks-badge>
                <ks-badge is-dot><ks-button>Dot</ks-button></ks-badge>
            </div>
        `,
    }),
}

/**
 * Inline – the pill sits in the normal flow instead of overlaying a child, for standalone counters
 * next to a label. This is how the tab strips and the settings sidebar render their counts, and it
 * replaces the `:deep(.kel-badge__content)` overrides those call sites used to carry.
 */
export const Inline: Story = {
    render: () => ({
        components: {KsBadge},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px;align-items:flex-start">
                <span style="display:inline-flex;align-items:center;gap:8px">
                    Executions
                    <ks-badge :value="12" type="primary" inline />
                </span>
                <span style="display:inline-flex;align-items:center;gap:8px">
                    Failed
                    <ks-badge :value="3" type="danger" inline />
                </span>
                <span style="display:inline-flex;align-items:center;gap:8px">
                    Capped by the default max
                    <ks-badge :value="1200" type="primary" inline />
                </span>
            </div>
        `,
    }),
}

/** Red dot – small dot without a number */
export const RedDot: Story = {
    render: () => ({
        components: {KsBadge, KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:24px;align-items:center">
                <ks-badge is-dot><ks-button>Notifications</ks-button></ks-badge>
                <ks-badge is-dot type="success"><ks-button>Updates</ks-button></ks-badge>
                <ks-badge is-dot type="warning"><ks-button>Warnings</ks-button></ks-badge>
            </div>
        `,
    }),
}

/** Hidden – badge can be hidden programmatically */
export const Hidden: Story = {
    render: () => ({
        components: {KsBadge, KsButton},
        setup() {
            const hidden = ref(false)
            return {hidden}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-badge :value="8" :hidden="hidden">
                    <ks-button>Messages</ks-button>
                </ks-badge>
                <button @click="hidden = !hidden">Toggle badge (hidden: {{ hidden }})</button>
            </div>
        `,
    }),
}

export const MaxValue: Story = {
    render: () => ({
        components: {KsBadge, KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:24px">
                <ks-badge :value="100" :max="99"><ks-button>Capped at 99</ks-button></ks-badge>
                <ks-badge :value="0" show-zero><ks-button>Show Zero</ks-button></ks-badge>
            </div>
        `,
    }),
}
