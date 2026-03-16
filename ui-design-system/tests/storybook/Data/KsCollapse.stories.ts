import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsCollapse from "../../../src/components/Data/KsCollapse/KsCollapse.vue"
import KsCollapseItem from "../../../src/components/Data/KsCollapse/KsCollapseItem.vue"

const meta: Meta<typeof KsCollapse> = {
    title: "Components/Data/KsCollapse",
    component: KsCollapse,
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
}
