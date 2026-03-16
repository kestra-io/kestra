import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsTabs from "../../../src/components/Navigation/KsTabs/KsTabs.vue"
import KsTabPane from "../../../src/components/Navigation/KsTabs/KsTabPane.vue"

const meta: Meta<typeof KsTabs> = {
    title: "Components/Navigation/KsTabs",
    component: KsTabs,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["", "card", "border-card"]},
    },
    parameters: {
        docs: {description: {component: "KsTabs is the Kestra design-system abstraction over `ElTabs` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTabs>

export const Default: Story = {
    render: (args) => ({
        components: {KsTabs, KsTabPane},
        setup() {
            const active = ref("overview")
            return {args, active}
        },
        template: `
            <div style="padding:24px">
                <ks-tabs v-model="active" v-bind="args">
                    <ks-tab-pane label="Overview" name="overview">Overview content</ks-tab-pane>
                    <ks-tab-pane label="Logs" name="logs">Logs content</ks-tab-pane>
                    <ks-tab-pane label="Metrics" name="metrics">Metrics content</ks-tab-pane>
                    <ks-tab-pane label="Disabled" name="disabled" disabled>Disabled</ks-tab-pane>
                </ks-tabs>
            </div>
        `,
    }),
}

export const Card: Story = {
    render: () => ({
        components: {KsTabs, KsTabPane},
        setup() { return {active: ref("tab1")} },
        template: `
            <div style="padding:24px">
                <ks-tabs v-model="active" type="card">
                    <ks-tab-pane label="Tab 1" name="tab1">Tab 1</ks-tab-pane>
                    <ks-tab-pane label="Tab 2" name="tab2">Tab 2</ks-tab-pane>
                    <ks-tab-pane label="Tab 3" name="tab3">Tab 3</ks-tab-pane>
                </ks-tabs>
            </div>
        `,
    }),
}

export const BorderCard: Story = {
    render: () => ({
        components: {KsTabs, KsTabPane},
        setup() { return {active: ref("tab1")} },
        template: `
            <div style="padding:24px">
                <ks-tabs v-model="active" type="border-card">
                    <ks-tab-pane label="Tab 1" name="tab1">Tab 1 content</ks-tab-pane>
                    <ks-tab-pane label="Tab 2" name="tab2">Tab 2 content</ks-tab-pane>
                </ks-tabs>
            </div>
        `,
    }),
}
