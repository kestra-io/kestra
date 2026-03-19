import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, expect} from "storybook/test"
import KsConfigProvider from "../../../src/components/Configuration/KsConfigProvider.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsInput from "../../../src/components/Form/KsInput.vue"

const meta: Meta<typeof KsConfigProvider> = {
    title: "Components/Configuration/KsConfigProvider",
    component: KsConfigProvider,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["small", "default", "large"]},
        zIndex: {control: "number"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsConfigProvider is the Kestra design-system abstraction over `ElConfigProvider` from Element Plus. " +
                    "It propagates global configuration (size, z-index, locale, etc.) to all descendant components.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsConfigProvider>

/** Default — no overrides, components use their own defaults */
export const Default: Story = {
    render: () => ({
        components: {KsConfigProvider, KsButton, KsInput},
        template: `
            <ks-config-provider>
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <ks-button type="primary">Button</ks-button>
                    <ks-input placeholder="Input" style="width:200px" />
                </div>
            </ks-config-provider>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(canvas.getByRole("button")).toBeTruthy()
        await expect(canvas.getByRole("textbox")).toBeTruthy()
    },
}

/** Global small size — all child components inherit small size */
export const GlobalSmallSize: Story = {
    render: () => ({
        components: {KsConfigProvider, KsButton, KsInput},
        template: `
            <ks-config-provider size="small">
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <ks-button type="primary">Small button</ks-button>
                    <ks-input placeholder="Small input" style="width:200px" />
                </div>
            </ks-config-provider>
        `,
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".kel-button--small")).toBeTruthy()
        await expect(canvasElement.querySelector(".kel-input--small")).toBeTruthy()
    },
}

/** Global large size */
export const GlobalLargeSize: Story = {
    render: () => ({
        components: {KsConfigProvider, KsButton, KsInput},
        template: `
            <ks-config-provider size="large">
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <ks-button type="primary">Large button</ks-button>
                    <ks-input placeholder="Large input" style="width:200px" />
                </div>
            </ks-config-provider>
        `,
    }),
}

/** Size comparison — wrap groups in separate providers */
export const SizeComparison: Story = {
    render: () => ({
        components: {KsConfigProvider, KsButton},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-config-provider size="large">
                    <div style="display:flex;gap:8px;align-items:center">
                        <span style="font-size:12px;opacity:0.5;width:60px">large</span>
                        <ks-button type="primary">Action</ks-button>
                        <ks-button>Secondary</ks-button>
                    </div>
                </ks-config-provider>
                <ks-config-provider size="default">
                    <div style="display:flex;gap:8px;align-items:center">
                        <span style="font-size:12px;opacity:0.5;width:60px">default</span>
                        <ks-button type="primary">Action</ks-button>
                        <ks-button>Secondary</ks-button>
                    </div>
                </ks-config-provider>
                <ks-config-provider size="small">
                    <div style="display:flex;gap:8px;align-items:center">
                        <span style="font-size:12px;opacity:0.5;width:60px">small</span>
                        <ks-button type="primary">Action</ks-button>
                        <ks-button>Secondary</ks-button>
                    </div>
                </ks-config-provider>
            </div>
        `,
    }),
}
