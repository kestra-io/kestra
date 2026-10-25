import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect} from "storybook/test"
import KsInput from "./KsInput.vue"

const meta: Meta<typeof KsInput> = {
    title: "Components/Form/KsInput",
    component: KsInput,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["text", "password", "textarea"]},
        size: {control: "select", options: ["small", "default", "large"]},
        disabled: {control: "boolean"},
        clearable: {control: "boolean"},
        showPassword: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsInput is the Kestra design-system abstraction over `ElInput` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsInput>

export const Default: Story = {
    render: (args) => ({
        components: {KsInput},
        setup() {
            const value = ref("")
            return {args, value}
        },
        template: "<div style=\"padding:24px;width:300px\"><ks-input v-model=\"value\" v-bind=\"args\" /></div>",
    }),
    args: {placeholder: "Type something..."},
}

export const Sizes: Story = {
    render: () => ({
        components: {KsInput},
        setup() {
            return {v1: ref(""), v2: ref(""), v3: ref("")}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:300px">
                <ks-input v-model="v1" size="large" placeholder="Large" />
                <ks-input v-model="v2" placeholder="Default" />
                <ks-input v-model="v3" size="small" placeholder="Small" />
            </div>
        `,
    }),
}

export const Password: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("")} },
        template: "<div style=\"padding:24px;width:300px\"><ks-input v-model=\"value\" type=\"password\" :show-password=\"true\" placeholder=\"Enter password\" /></div>",
    }),
}

export const TextArea: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("")} },
        template: "<div style=\"padding:24px;width:300px\"><ks-input v-model=\"value\" type=\"textarea\" :rows=\"4\" placeholder=\"Enter text...\" /></div>",
    }),
}

/** Clearable – clear button when input has value */
export const Clearable: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("Clear me")} },
        template: "<div style=\"padding:24px;width:300px\"><ks-input v-model=\"value\" clearable placeholder=\"Type to fill...\" /></div>",
    }),
}

/**
 * Clearable inside a shrink-to-fit container. The wrapper sizes to its content, so a naive
 * clearable input would grow when the clear icon appears on hover and shrink when it leaves.
 * KsInput reserves the icon's footprint, so the width stays stable. Hover to verify it doesn't jump.
 */
export const ClearableNoReflow: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("Hover me – width stays fixed")} },
        template: `
            <div style="padding:24px;display:inline-flex;border:1px dashed var(--ks-border-default)">
                <ks-input v-model="value" clearable placeholder="Type to fill..." />
            </div>
        `,
    }),
}

/** Borderless – flush in a list row, as the search of the KsFilter "Add filters" panel */
export const Borderless: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("")} },
        template: `
            <div style="padding:24px;width:300px">
                <div style="padding:var(--ks-spacing-2) var(--ks-spacing-4);border-block:1px solid var(--ks-border-default)">
                    <ks-input v-model="value" borderless clearable size="small" placeholder="Search..." />
                </div>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const wrapper = canvasElement.querySelector(".kel-input__wrapper") as HTMLElement
        const style = getComputedStyle(wrapper)
        await expect(style.backgroundColor).toBe("rgba(0, 0, 0, 0)")
        await expect(style.boxShadow).toMatch(/^rgba\(0, 0, 0, 0\)/)
        await expect(style.paddingLeft).toBe("0px")
    },
}

/** With suffix slot */
export const WithSuffix: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("")} },
        template: `
            <div style="padding:24px;width:300px">
                <ks-input v-model="value" placeholder="Search...">
                    <template #suffix>🔍</template>
                </ks-input>
            </div>
        `,
    }),
}

/** Mixed input – prepend and append */
export const MixedInput: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {v1: ref(""), v2: ref("")} },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:360px">
                <ks-input v-model="v1" placeholder="website">
                    <template #prepend>https://</template>
                </ks-input>
                <ks-input v-model="v2" placeholder="domain">
                    <template #prepend>http://</template>
                    <template #suffix>.io</template>
                </ks-input>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("Disabled value")} },
        template: "<div style=\"padding:24px;width:300px\"><ks-input v-model=\"value\" disabled /></div>",
    }),
}

/** Mobile – on touch screens (`pointer: coarse`) the field is 44px high with 16px text, which stops iOS from zooming on focus */
export const Mobile: Story = {
    parameters: {
        viewport: {
            options: {
                mobile: {name: "Mobile", styles: {width: "390px", height: "844px"}},
            },
        },
    },
    globals: {
        viewport: {value: "mobile", isRotated: false},
    },
    render: () => ({
        components: {KsInput},
        setup() { return {email: ref(""), password: ref("")} },
        template: `
            <div style="padding:16px;display:flex;flex-direction:column;gap:12px">
                <ks-input v-model="email" placeholder="Email" />
                <ks-input v-model="password" placeholder="Password" show-password />
                <ks-input v-model="email" type="textarea" placeholder="Notes" />
            </div>
        `,
    }),
}
