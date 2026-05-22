import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsTabs from "../../../src/components/Navigation/KsTabs/KsTabs.vue"
import KsTabPane from "../../../src/components/Navigation/KsTabs/KsTabPane.vue"

const meta: Meta<typeof KsTabs> = {
    title: "Components/Navigation/KsTabs",
    component: KsTabs,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["", "box", "card", "border-card"]},
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
                <ks-tabs v-model="active" type="box" v-bind="args">
                    <ks-tab-pane label="Overview" name="overview">Overview content</ks-tab-pane>
                    <ks-tab-pane label="Logs" name="logs">Logs content</ks-tab-pane>
                    <ks-tab-pane label="Metrics" name="metrics">Metrics content</ks-tab-pane>
                    <ks-tab-pane label="Disabled" name="disabled" disabled>Disabled</ks-tab-pane>
                </ks-tabs>
            </div>
        `,
    }),
}

export const Box: Story = {
    render: () => ({
        components: {KsTabs, KsTabPane},
        setup() { return {active: ref("tab1")} },
        template: `
            <div style="padding:24px">
                <ks-tabs v-model="active" type="box">
                    <ks-tab-pane label="Tab 1" name="tab1">Tab 1</ks-tab-pane>
                    <ks-tab-pane label="Tab 2" name="tab2">Tab 2</ks-tab-pane>
                    <ks-tab-pane label="Tab 3" name="tab3">Tab 3</ks-tab-pane>
                    <ks-tab-pane label="Tab 4" name="tab4">Tab 4</ks-tab-pane>
                    <ks-tab-pane label="Tab 5" name="tab5">Tab 5</ks-tab-pane>
                    <ks-tab-pane label="Tab 6" name="tab6">Tab 6</ks-tab-pane>
                    <ks-tab-pane label="Tab 7" name="tab7">Tab 7</ks-tab-pane>
                    <ks-tab-pane label="Tab 8" name="tab8">Tab 8</ks-tab-pane>
                </ks-tabs>
            </div>
        `,
    }),
}

/** Tab position – top, right, bottom, left */
export const TabPosition: Story = {
    render: () => ({
        components: {KsTabs, KsTabPane},
        setup() { return {active: ref("a"), active2: ref("a"), active3: ref("a"), active4: ref("a")} },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:32px">
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 8px">top</p>
                    <ks-tabs v-model="active" tab-position="top">
                        <ks-tab-pane label="Tab A" name="a">Content A</ks-tab-pane>
                        <ks-tab-pane label="Tab B" name="b">Content B</ks-tab-pane>
                    </ks-tabs>
                </div>
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 8px">bottom</p>
                    <ks-tabs v-model="active2" tab-position="bottom">
                        <ks-tab-pane label="Tab A" name="a">Content A</ks-tab-pane>
                        <ks-tab-pane label="Tab B" name="b">Content B</ks-tab-pane>
                    </ks-tabs>
                </div>
            </div>
        `,
    }),
}

/** Custom tab label via slot */
export const CustomLabel: Story = {
    render: () => ({
        components: {KsTabs, KsTabPane},
        setup() { return {active: ref("flows")} },
        template: `
            <div style="padding:24px">
                <ks-tabs v-model="active">
                    <ks-tab-pane name="flows">
                        <template #label>
                            <span style="display:flex;align-items:center;gap:4px">
                                <span>⚡</span> Flows
                            </span>
                        </template>
                        Flows content
                    </ks-tab-pane>
                    <ks-tab-pane name="executions">
                        <template #label>
                            <span style="display:flex;align-items:center;gap:4px">
                                <span>▶</span> Executions
                            </span>
                        </template>
                        Executions content
                    </ks-tab-pane>
                </ks-tabs>
            </div>
        `,
    }),
}

