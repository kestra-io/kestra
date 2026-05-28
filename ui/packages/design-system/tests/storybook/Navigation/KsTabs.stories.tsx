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
    render: () => ({
        setup(args) {
            const active = ref("overview")
            return () => (
                <div style="padding:24px">
                    <KsTabs v-model={active.value} type="box" {...args}>
                        <KsTabPane label="Overview" name="overview">Overview content</KsTabPane>
                        <KsTabPane label="Logs" name="logs">Logs content</KsTabPane>
                        <KsTabPane label="Metrics" name="metrics">Metrics content</KsTabPane>
                        <KsTabPane label="Disabled" name="disabled" disabled>Disabled</KsTabPane>
                    </KsTabs>
                </div>
            )
        },
    }),
}

export const Box: Story = {
    render: () => ({
        setup() { 
            const active = ref("tab1")
            return () => (
                <div style="padding:24px">
                    <KsTabs v-model={active.value} type="box">
                        <KsTabPane label="Tab 1" name="tab1">Tab 1</KsTabPane>
                        <KsTabPane label="Tab 2" name="tab2">Tab 2</KsTabPane>
                        <KsTabPane label="Tab 3" name="tab3">Tab 3</KsTabPane>
                        <KsTabPane label="Tab 4" name="tab4">Tab 4</KsTabPane>
                        <KsTabPane label="Tab 5" name="tab5">Tab 5</KsTabPane>
                        <KsTabPane label="Tab 6" name="tab6">Tab 6</KsTabPane>
                        <KsTabPane label="Tab 7" name="tab7">Tab 7</KsTabPane>
                        <KsTabPane label="Tab 8" name="tab8">Tab 8</KsTabPane>
                    </KsTabs>
                </div>
            )
        },
    }),
}

/** Tab position – top, right, bottom, left */
export const TabPosition: Story = {
    render: () => ({
        setup() { 
            const active = ref("a")
            const active2 = ref("a")
            const active3 = ref("a")
            const active4 = ref("a")
            return () => (
                <div style="padding:24px;display:flex;flex-direction:column;gap:32px">
                    <div>
                        <p style="font-size:12px;opacity:0.5;margin:0 0 8px">top</p>
                        <KsTabs v-model={active.value} tab-position="top">
                            <KsTabPane label="Tab A" name="a">Content A</KsTabPane>
                            <KsTabPane label="Tab B" name="b">Content B</KsTabPane>
                        </KsTabs>
                    </div>
                    <div>
                        <p style="font-size:12px;opacity:0.5;margin:0 0 8px">bottom</p>
                        <KsTabs v-model={active2.value} tab-position="bottom">
                            <KsTabPane label="Tab A" name="a">Content A</KsTabPane>
                            <KsTabPane label="Tab B" name="b">Content B</KsTabPane>
                        </KsTabs>
                    </div>
                    <div>
                        <p style="font-size:12px;opacity:0.5;margin:0 0 8px">left</p>
                        <KsTabs v-model={active3.value} tab-position="left">
                            <KsTabPane label="Tab A" name="a">Content A</KsTabPane>
                            <KsTabPane label="Tab B" name="b">Content B</KsTabPane>
                        </KsTabs>
                    </div>
                    <div>
                        <p style="font-size:12px;opacity:0.5;margin:0 0 8px">right</p>
                        <KsTabs v-model={active4.value} tab-position="right">
                            <KsTabPane label="Tab A" name="a">Content A</KsTabPane>
                            <KsTabPane label="Tab B" name="b">Content B</KsTabPane>
                        </KsTabs>
                    </div>
                </div>
            )
        },
    }),
}

/** Custom tab label via slot */
export const CustomLabel: Story = {
    render: () => ({
        setup() { 
            const active = ref("flows")
            return () => (
                <div style="padding:24px">
                    <KsTabs v-model={active.value}>
                        <KsTabPane name="flows">
                            {{
                                label: () => (
                                    <span style="display:flex;align-items:center;gap:4px">
                                        <span>⚡</span> Flows
                                    </span>
                                ),
                                default: () => "Flows content"
                            }}

                        </KsTabPane>
                        <KsTabPane name="executions">
                            {{
                                label: () => (
                                    <span style="display:flex;align-items:center;gap:4px">
                                        <span>▶</span> Executions
                                    </span>
                                ),
                                default: () => "Executions content"
                            }}
                        </KsTabPane>
                    </KsTabs>
                </div>
            )
        },
    }),
}

