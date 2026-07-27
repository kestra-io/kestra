import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsEntityLink from "../../../src/components/Data/KsEntityLink/KsEntityLink.vue"

const meta: Meta<typeof KsEntityLink> = {
    title: "Components/Data/KsEntityLink",
    component: KsEntityLink,
    tags: ["autodocs"],
    argTypes: {
        entity: {control: "select", options: ["namespace", "flow"]},
    },
    parameters: {
        docs: {description: {component: "Clickable cross-entity reference (namespace or flow) used in table cells: a neutral tag with a leading entity icon, turning violet on hover so rest-state rows keep their visual hierarchy (kestra-ee#9432)."}},
    },
}
export default meta
type Story = StoryObj<typeof KsEntityLink>

export const Namespace: Story = {
    render: (args) => ({
        components: {KsEntityLink},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-entity-link v-bind=\"args\" /></div>",
    }),
    args: {
        entity: "namespace",
        value: "company.team",
        to: "/namespaces/edit/company.team",
    },
}

export const Flow: Story = {
    render: (args) => ({
        components: {KsEntityLink},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-entity-link v-bind=\"args\" /></div>",
    }),
    args: {
        entity: "flow",
        value: "order_pipeline",
        to: "/flows/edit/company.team/order_pipeline",
    },
}

export const Truncated: Story = {
    render: () => ({
        components: {KsEntityLink},
        template: `
            <div style="padding:24px;width:8.5rem;border:1px dashed var(--ks-border-default)">
                <ks-entity-link
                    entity="namespace"
                    value="sanitychecks.plugins.plugin-ansible"
                    to="/namespaces/edit/sanitychecks.plugins.plugin-ansible"
                />
            </div>
        `,
    }),
}

export const States: Story = {
    render: () => ({
        components: {KsEntityLink},
        template: `
            <div style="padding:24px;display:flex;gap:32px;align-items:end">
                <div style="display:grid;gap:8px;justify-items:start">
                    <span style="font-size:11px;text-transform:uppercase;color:var(--ks-text-dim)">Rest</span>
                    <ks-entity-link entity="namespace" value="company.team" to="/namespaces/edit/company.team" />
                </div>
                <div style="display:grid;gap:8px;justify-items:start">
                    <span style="font-size:11px;text-transform:uppercase;color:var(--ks-text-dim)">Hover</span>
                    <ks-entity-link
                        entity="namespace"
                        value="company.team"
                        to="/namespaces/edit/company.team"
                        style="background: var(--ks-bg-tag-hover); text-decoration: underline;"
                    />
                </div>
                <div style="display:grid;gap:8px;justify-items:start">
                    <span style="font-size:11px;text-transform:uppercase;color:var(--ks-text-dim)">Focus</span>
                    <ks-entity-link
                        entity="namespace"
                        value="company.team"
                        to="/namespaces/edit/company.team"
                        style="outline: 2px solid var(--ks-border-focus); outline-offset: 1px;"
                    />
                </div>
                <div style="display:grid;gap:8px;justify-items:start">
                    <span style="font-size:11px;text-transform:uppercase;color:var(--ks-text-dim)">Active</span>
                    <ks-entity-link
                        entity="namespace"
                        value="company.team"
                        to="/namespaces/edit/company.team"
                        style="background: var(--ks-bg-tag-active);"
                    />
                </div>
            </div>
        `,
    }),
}
