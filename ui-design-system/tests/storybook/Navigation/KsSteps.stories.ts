import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsSteps from "../../../src/components/Navigation/KsSteps/KsSteps.vue"
import KsStep from "../../../src/components/Navigation/KsSteps/KsStep.vue"

const meta: Meta<typeof KsSteps> = {
    title: "Components/Navigation/KsSteps",
    component: KsSteps,
    tags: ["autodocs"],
    argTypes: {
        direction: {control: "select", options: ["horizontal", "vertical"]},
    },
    parameters: {
        docs: {description: {component: "KsSteps is the Kestra design-system abstraction over `ElSteps` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSteps>

export const Default: Story = {
    render: (args) => ({
        components: {KsSteps, KsStep},
        setup() {
            const active = ref(1)
            return {args, active}
        },
        template: `
            <div style="padding:24px">
                <ks-steps :active="active" v-bind="args">
                    <ks-step title="Step 1" description="Configure namespace" />
                    <ks-step title="Step 2" description="Set up authentication" />
                    <ks-step title="Step 3" description="Deploy flows" />
                </ks-steps>
                <div style="margin-top:16px;display:flex;gap:8px">
                    <button @click="active = Math.max(0, active - 1)">Previous</button>
                    <button @click="active = Math.min(3, active + 1)">Next</button>
                </div>
            </div>
        `,
    }),
}

export const Vertical: Story = {
    render: () => ({
        components: {KsSteps, KsStep},
        setup() { return {active: ref(2)} },
        template: `
            <div style="padding:24px">
                <ks-steps :active="active" direction="vertical">
                    <ks-step title="Create namespace" />
                    <ks-step title="Configure secrets" />
                    <ks-step title="Add flows" />
                </ks-steps>
            </div>
        `,
    }),
}
