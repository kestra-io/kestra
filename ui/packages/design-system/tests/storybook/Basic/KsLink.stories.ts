import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsLink from "../../../src/components/Basic/KsLink.vue"

const meta: Meta<typeof KsLink> = {
    title: "Components/Basic/KsLink",
    component: KsLink,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["", "default", "primary", "success", "warning", "danger", "info"]},
        underline: {control: "boolean"},
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsLink is the Kestra design-system abstraction over `ElLink` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsLink>

export const Default: Story = {
    render: (args) => ({
        components: {KsLink},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-link v-bind=\"args\">Click me</ks-link></div>",
    }),
    args: {type: "primary", href: "#"},
}

export const Types: Story = {
    render: () => ({
        components: {KsLink},
        template: `
            <div style="padding:24px;display:flex;gap:16px;flex-wrap:wrap">
                <ks-link>Default</ks-link>
                <ks-link type="primary">Primary</ks-link>
                <ks-link type="success">Success</ks-link>
                <ks-link type="info">Info</ks-link>
                <ks-link type="warning">Warning</ks-link>
                <ks-link type="danger">Danger</ks-link>
            </div>
        `,
    }),
}

/** Underline control */
export const Underline: Story = {
    render: () => ({
        components: {KsLink},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:8px">
                <ks-link type="primary" underline="always">With underline</ks-link>
                <ks-link type="primary" underline="hover">Hover underline</ks-link>
                <ks-link type="primary" underline="never">Without underline</ks-link>
            </div>
        `,
    }),
}

/** With icon slot */
export const WithIcon: Story = {
    render: () => ({
        components: {KsLink},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center;flex-wrap:wrap">
                <ks-link type="primary" href="#">
                    <template #icon>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                    </template>
                    Open in new tab
                </ks-link>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsLink},
        template: `
            <div style="padding:24px;display:flex;gap:16px">
                <ks-link type="primary" disabled>Disabled Link</ks-link>
            </div>
        `,
    }),
}
