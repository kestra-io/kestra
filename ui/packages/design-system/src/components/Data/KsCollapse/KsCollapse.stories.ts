import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref, type ConcreteComponent} from "vue"
import {expect} from "storybook/test"
import KsCollapse from "./KsCollapse.vue"
import KsCollapseItem from "./KsCollapseItem.vue"

const meta: Meta<typeof KsCollapse> = {
    title: "Components/Data/KsCollapse",
    component: KsCollapse as ConcreteComponent,
    tags: ["autodocs"],
    argTypes: {
        accordion: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsCollapse is the Kestra design-system abstraction over `ElCollapse` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsCollapse>

export const Default: Story = {
    render: (args) => ({
        components: {KsCollapse, KsCollapseItem},
        setup() {
            const active = ref(["1"])
            return {args, active}
        },
        template: `
            <div style="padding:24px;max-width:600px">
                <ks-collapse v-model="active" v-bind="args">
                    <ks-collapse-item title="General Settings" name="1">
                        <p>Configure general workflow settings here.</p>
                    </ks-collapse-item>
                    <ks-collapse-item title="Advanced Options" name="2">
                        <p>Advanced configuration options.</p>
                    </ks-collapse-item>
                    <ks-collapse-item title="Notifications" name="3">
                        <p>Manage notification preferences.</p>
                    </ks-collapse-item>
                </ks-collapse>
            </div>
        `,
    }),
    args: {accordion: false},
}

/** Custom title – header content via named slot */
export const CustomTitle: Story = {
    render: () => ({
        components: {KsCollapse, KsCollapseItem},
        setup() { return {active: ref(["1"])} },
        template: `
            <div style="padding:24px;max-width:600px">
                <ks-collapse v-model="active">
                    <ks-collapse-item name="1">
                        <template #title>
                            <span style="display:flex;align-items:center;gap:8px">
                                <span style="font-size:16px">⚙️</span>
                                <strong>General Settings</strong>
                                <span style="font-size:11px;opacity:0.5;margin-left:4px">required</span>
                            </span>
                        </template>
                        <p>Configure general workflow settings here.</p>
                    </ks-collapse-item>
                    <ks-collapse-item name="2">
                        <template #title>
                            <span style="display:flex;align-items:center;gap:8px">
                                <span style="font-size:16px">🔔</span>
                                <strong>Notifications</strong>
                            </span>
                        </template>
                        <p>Manage notification preferences.</p>
                    </ks-collapse-item>
                </ks-collapse>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

/** Disabled item – specific panel cannot be toggled */
export const DisabledItem: Story = {
    render: () => ({
        components: {KsCollapse, KsCollapseItem},
        setup() { return {active: ref(["1"])} },
        template: `
            <div style="padding:24px;max-width:600px">
                <ks-collapse v-model="active">
                    <ks-collapse-item title="Enabled item" name="1">
                        <p>This item can be toggled.</p>
                    </ks-collapse-item>
                    <ks-collapse-item title="Disabled item" name="2" disabled>
                        <p>This content cannot be toggled.</p>
                    </ks-collapse-item>
                    <ks-collapse-item title="Another enabled" name="3">
                        <p>Another toggleable item.</p>
                    </ks-collapse-item>
                </ks-collapse>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

export const Accordion: Story = {
    render: () => ({
        components: {KsCollapse, KsCollapseItem},
        setup() { return {active: ref("1")} },
        template: `
            <div style="padding:24px;max-width:600px">
                <ks-collapse v-model="active" accordion>
                    <ks-collapse-item title="Section 1" name="1">Content for section 1.</ks-collapse-item>
                    <ks-collapse-item title="Section 2" name="2">Content for section 2.</ks-collapse-item>
                    <ks-collapse-item title="Section 3" name="3">Content for section 3.</ks-collapse-item>
                </ks-collapse>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

/** Flush: a compact navigation tree, no padding and the arrow beside the title (the docs table of contents) */
export const Flush: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsCollapse, KsCollapseItem},
        setup() { return {active: ref(["1"])} },
        template: `
            <div style="padding:24px;max-width:300px">
                <ks-collapse v-model="active" accordion flush>
                    <ks-collapse-item title="Getting started" name="1">
                        <a href="#">Installation</a>
                    </ks-collapse-item>
                    <ks-collapse-item title="Concepts" name="2">
                        <a href="#">Flows</a>
                    </ks-collapse-item>
                </ks-collapse>
            </div>
        `,
    }),
}

/** Header and content knobs, set on the collapse or an ancestor: `--ks-collapse-header-padding`, `--ks-collapse-header-border` (kept while the item is open), `--ks-collapse-header-radius`, `--ks-collapse-header-bg` and `--ks-collapse-content-padding` */
export const StyleVariables: Story = {
    render: () => ({
        components: {KsCollapse, KsCollapseItem},
        setup() { return {active: ref(["1"])} },
        template: `
            <div style="padding:24px;max-width:600px;--ks-collapse-header-padding:var(--ks-spacing-1) var(--ks-spacing-4);--ks-collapse-header-border:1px solid var(--ks-btn-secondary-border-default);--ks-collapse-header-radius:var(--ks-radius-base);--ks-collapse-header-bg:var(--ks-btn-secondary-bg-default);--ks-collapse-content-padding:var(--ks-spacing-3)">
                <ks-collapse v-model="active">
                    <ks-collapse-item title="Debug information" name="1">
                        <p>Collected automatically with the request.</p>
                    </ks-collapse-item>
                </ks-collapse>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
    async play({canvasElement}) {
        const header = getComputedStyle(canvasElement.querySelector(".kel-collapse-item__header")!)
        await expect(header.paddingLeft).toBe("16px")
        await expect(header.borderTopLeftRadius).toBe("8px")
        await expect(header.borderBottomStyle).toBe("solid")
        await expect(getComputedStyle(canvasElement.querySelector(".kel-collapse-item__content")!).paddingTop).toBe("12px")
    },
}
