import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect} from "storybook/test"
import KsRadio from "./KsRadio.vue"
import KsRadioGroup from "./KsRadioGroup.vue"
import KsRadioButton from "./KsRadioButton.vue"

const meta: Meta<typeof KsRadio> = {
    title: "Components/Form/KsRadio",
    component: KsRadio,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsRadio is the Kestra design-system abstraction over `ElRadio` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsRadio>

export const Default: Story = {
    render: (args) => ({
        components: {KsRadio},
        setup() {
            const value = ref("A")
            return {args, value}
        },
        template: `
            <div style="padding:24px;display:flex;gap:16px">
                <ks-radio v-model="value" value="A" v-bind="args">Option A</ks-radio>
                <ks-radio v-model="value" value="B" v-bind="args">Option B</ks-radio>
            </div>
            <div style="padding:24px;display:flex;gap:16px">
                <span style="font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
    args: {disabled: false},
}

export const Group: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsRadioGroup, KsRadio},
        setup() {
            const value = ref("daily")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value">
                    <ks-radio value="daily">Daily</ks-radio>
                    <ks-radio value="weekly">Weekly</ks-radio>
                    <ks-radio value="monthly">Monthly</ks-radio>
                </ks-radio-group>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const ButtonGroup: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsRadioGroup, KsRadioButton},
        setup() {
            const value = ref("left")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value">
                    <ks-radio-button value="left">Left</ks-radio-button>
                    <ks-radio-button value="center">Center</ks-radio-button>
                    <ks-radio-button value="right">Right</ks-radio-button>
                </ks-radio-group>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const Sizes: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsRadioGroup, KsRadioButton},
        setup() {
            return {v1: ref("a"), v2: ref("a"), v3: ref("a")}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-radio-group v-model="v1" size="large">
                    <ks-radio-button value="a">Large A</ks-radio-button>
                    <ks-radio-button value="b">Large B</ks-radio-button>
                </ks-radio-group>
                <ks-radio-group v-model="v2">
                    <ks-radio-button value="a">Default A</ks-radio-button>
                    <ks-radio-button value="b">Default B</ks-radio-button>
                </ks-radio-group>
                <ks-radio-group v-model="v3" size="small">
                    <ks-radio-button value="a">Small A</ks-radio-button>
                    <ks-radio-button value="b">Small B</ks-radio-button>
                </ks-radio-group>
            </div>

            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-radio-group v-model="v1" size="large">
                    <ks-radio value="a">Large A</ks-radio>
                    <ks-radio value="b">Large B</ks-radio>
                </ks-radio-group>
                <ks-radio-group v-model="v2">
                    <ks-radio value="a">Default A</ks-radio>
                    <ks-radio value="b">Default B</ks-radio>
                </ks-radio-group>
                <ks-radio-group v-model="v3" size="small">
                    <ks-radio value="a">Small A</ks-radio>
                    <ks-radio value="b">Small B</ks-radio>
                </ks-radio-group>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsRadioGroup, KsRadio},
        setup() { return {value: ref("B")} },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value">
                    <ks-radio disabled value="A">Option A</ks-radio>
                    <ks-radio disabled value="B">Option B</ks-radio>
                </ks-radio-group>
            </div>

            <div style="padding:24px">
                <ks-radio-group v-model="value">
                    <ks-radio-button disabled value="A">Option A</ks-radio-button>
                    <ks-radio-button disabled value="B">Option B</ks-radio-button>
                </ks-radio-group>
            </div>
        `,
    }),
}

/** Radio button knobs, set on the group or an ancestor: `--ks-radio-button-padding`, `--ks-radio-button-radius` (every corner, wherever the button sits), `--ks-radio-button-bg`, `--ks-radio-button-active-bg`, `--ks-radio-button-active-color` and `--ks-radio-button-active-border-color` */
export const ButtonVariables: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsRadioGroup, KsRadioButton},
        setup() { return {value: ref("all")} },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value" style="display:flex;gap:8px;--ks-radio-button-padding:var(--ks-spacing-2) var(--ks-spacing-3);--ks-radio-button-radius:var(--ks-radius-sm);--ks-radio-button-bg:var(--ks-btn-secondary-bg-default);--ks-radio-button-active-bg:var(--ks-btn-primary-bg-default);--ks-radio-button-active-color:var(--ks-white);--ks-radio-button-active-border-color:var(--ks-border-default)">
                    <ks-radio-button value="all">All</ks-radio-button>
                    <ks-radio-button value="data">Data</ks-radio-button>
                    <ks-radio-button value="ops">Ops</ks-radio-button>
                </ks-radio-group>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const inners = [...canvasElement.querySelectorAll(".kel-radio-button__inner")].map(inner => getComputedStyle(inner))
        for (const inner of inners) {
            await expect(inner.paddingTop).toBe("8px")
            await expect(inner.paddingLeft).toBe("12px")
            await expect(inner.borderTopLeftRadius).toBe("6px")
            await expect(inner.borderBottomRightRadius).toBe("6px")
        }
        await expect(inners[0].color).toBe("rgb(255, 255, 255)")
        await expect(inners[1].backgroundColor).not.toBe(inners[0].backgroundColor)
    },
}
