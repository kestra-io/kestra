import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {markRaw} from "vue"
import {within, expect} from "storybook/test"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsButtonGroup from "../../../src/components/Basic/KsButton/KsButtonGroup.vue"

const PrevIcon = markRaw({
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1em\" height=\"1em\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"15 18 9 12 15 6\"/></svg>",
})
const NextIcon = markRaw({
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1em\" height=\"1em\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"9 18 15 12 9 6\"/></svg>",
})

const meta: Meta<typeof KsButtonGroup> = {
    title: "Components/Basic/KsButtonGroup",
    component: KsButtonGroup,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["small", "default", "large"]},
        direction: {control: "select", options: ["horizontal", "vertical"]},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsButtonGroup is the Kestra design-system abstraction over `ElButtonGroup` from Element Plus. " +
                    "Use it to group related `KsButton` elements into a single cohesive control.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsButtonGroup>

/** Default horizontal group */
export const Default: Story = {
    render: (args) => ({
        components: {KsButton, KsButtonGroup},
        setup() {
            return {args, PrevIcon, NextIcon}
        },
        template: `
            <div style="padding:24px">
                <ks-button-group v-bind="args">
                    <ks-button type="primary" :icon="PrevIcon">Previous</ks-button>
                    <ks-button type="primary" :icon="NextIcon">Next</ks-button>
                </ks-button-group>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const buttons = canvas.getAllByRole("button")
        await expect(buttons.length).toBe(2)
    },
}

/** All types */
export const Types: Story = {
    render: () => ({
        components: {KsButton, KsButtonGroup},
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:16px;align-items:center">
                <ks-button-group>
                    <ks-button>Left</ks-button>
                    <ks-button>Center</ks-button>
                    <ks-button>Right</ks-button>
                </ks-button-group>
                <ks-button-group type="primary">
                    <ks-button>Left</ks-button>
                    <ks-button>Center</ks-button>
                    <ks-button>Right</ks-button>
                </ks-button-group>
                <ks-button-group type="success">
                    <ks-button>Left</ks-button>
                    <ks-button>Right</ks-button>
                </ks-button-group>
                <ks-button-group type="danger">
                    <ks-button>Left</ks-button>
                    <ks-button>Right</ks-button>
                </ks-button-group>
            </div>
        `,
    }),
}

/** Sizes */
export const Sizes: Story = {
    render: () => ({
        components: {KsButton, KsButtonGroup},
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:16px;align-items:center">
                <ks-button-group size="large" type="primary">
                    <ks-button>Large</ks-button>
                    <ks-button>Group</ks-button>
                </ks-button-group>
                <ks-button-group type="primary">
                    <ks-button>Default</ks-button>
                    <ks-button>Group</ks-button>
                </ks-button-group>
                <ks-button-group size="small" type="primary">
                    <ks-button>Small</ks-button>
                    <ks-button>Group</ks-button>
                </ks-button-group>
            </div>
        `,
    }),
}

/** Vertical direction */
export const Vertical: Story = {
    render: () => ({
        components: {KsButton, KsButtonGroup},
        template: `
            <div style="padding:24px">
                <ks-button-group direction="vertical" type="primary">
                    <ks-button>Top</ks-button>
                    <ks-button>Middle</ks-button>
                    <ks-button>Bottom</ks-button>
                </ks-button-group>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const group = canvasElement.querySelector(".kel-button-group--vertical")
        await expect(group).toBeTruthy()
        const buttons = canvas.getAllByRole("button")
        await expect(buttons.length).toBe(3)
    },
}

/** Icon-only group – as used in PrevNext navigation */
export const IconOnly: Story = {
    render: () => ({
        components: {KsButton, KsButtonGroup},
        setup() {
            return {PrevIcon, NextIcon}
        },
        template: `
            <div style="padding:24px">
                <ks-button-group type="primary">
                    <ks-button :icon="PrevIcon" />
                    <ks-button :icon="NextIcon" />
                </ks-button-group>
            </div>
        `,
    }),
}
