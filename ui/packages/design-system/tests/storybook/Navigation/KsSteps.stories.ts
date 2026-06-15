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
        docs: {description: {component: "KsSteps is the Kestra design-system abstraction over `ElSteps` from Element Plus."}},
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
                    <ks-step title="Step 1" description="First step" />
                    <ks-step title="Step 2" description="Second step" />
                    <ks-step title="Step 3" description="Third step" />
                </ks-steps>
                <div style="margin-top:16px;display:flex;gap:8px">
                    <button @click="active = Math.max(0, active - 1)">Previous</button>
                    <button @click="active = Math.min(3, active + 1)">Next</button>
                </div>
            </div>
        `,
    }),
}

/** With finish status – shows completion state */
export const WithStatus: Story = {
    render: () => ({
        components: {KsSteps, KsStep},
        template: `
            <div style="padding:24px">
                <ks-steps :active="2" finish-status="success">
                    <ks-step title="Step 1" />
                    <ks-step title="Step 2" />
                    <ks-step title="Step 3" />
                    <ks-step title="Step 4" />
                </ks-steps>
            </div>
        `,
    }),
}

/** Center aligned titles */
export const AlignCenter: Story = {
    render: () => ({
        components: {KsSteps, KsStep},
        template: `
            <div style="padding:24px">
                <ks-steps :active="1" align-center>
                    <ks-step title="Step 1" description="First step" />
                    <ks-step title="Step 2" description="Second step" />
                    <ks-step title="Step 3" description="Third step" />
                </ks-steps>
            </div>
        `,
    }),
}

/** Simple step bar */
export const Simple: Story = {
    render: () => ({
        components: {KsSteps, KsStep},
        setup() { return {active: ref(1)} },
        template: `
            <div style="padding:24px">
                <ks-steps :active="active" simple>
                    <ks-step title="Step 1" />
                    <ks-step title="Step 2" />
                    <ks-step title="Step 3" />
                </ks-steps>
                <div style="margin-top:12px;display:flex;gap:8px">
                    <button @click="active = Math.max(0, active - 1)">Prev</button>
                    <button @click="active = Math.min(3, active + 1)">Next</button>
                </div>
            </div>
        `,
    }),
}

/** Horizontal with align-center — connector line spans the gap between step heads */
export const HorizontalWithConnector: Story = {
    render: () => ({
        components: {KsSteps, KsStep},
        setup() { return {active: ref(1)} },
        template: `
            <div style="padding:24px">
                <ks-steps :active="active" align-center finish-status="success">
                    <ks-step title="Step 1" />
                    <ks-step title="Step 2" />
                    <ks-step title="Step 3" />
                    <ks-step title="Step 4" />
                </ks-steps>
                <div style="margin-top:12px;display:flex;gap:8px">
                    <button @click="active = Math.max(0, active - 1)">Prev</button>
                    <button @click="active = Math.min(4, active + 1)">Next</button>
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
                    <ks-step title="Step 1" />
                    <ks-step title="Step 2" />
                    <ks-step title="Step 3" />
                </ks-steps>
            </div>
        `,
    }),
}
