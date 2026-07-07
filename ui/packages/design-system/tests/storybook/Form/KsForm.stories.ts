import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import {reactive} from "vue"
import KsForm from "../../../src/components/Form/KsForm/KsForm.vue"
import KsFormItem from "../../../src/components/Form/KsForm/KsFormItem.vue"
import KsInput from "../../../src/components/Form/KsInput.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsForm> = {
    title: "Components/Form/KsForm",
    component: KsForm,
    tags: ["autodocs"],
    argTypes: {
        labelPosition: {control: "select", options: ["top", "left", "right"]},
        disabled: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
    },
    parameters: {
        docs: {description: {component: "KsForm is the Kestra design-system abstraction over `ElForm` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsForm>

export const Default: Story = {
    render: (args) => ({
        components: {KsForm, KsFormItem, KsInput, KsButton},
        setup() {
            const form = reactive({name: "", email: ""})
            const rules = {
                name: [{required: true, message: "Name is required", trigger: "blur"}],
                email: [{type: "email", message: "Valid email required", trigger: "blur"}],
            }
            return {args, form, rules}
        },
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-form :model="form" :rules="rules" v-bind="args" label-position="top">
                    <ks-form-item label="Name" prop="name">
                        <ks-input v-model="form.name" placeholder="Enter name" />
                    </ks-form-item>
                    <ks-form-item label="Email" prop="email">
                        <ks-input v-model="form.email" placeholder="Enter email" />
                    </ks-form-item>
                    <ks-form-item>
                        <ks-button type="primary" native-type="submit">Submit</ks-button>
                    </ks-form-item>
                </ks-form>
            </div>
        `,
    }),
}

/** Inline form */
export const InlineForm: Story = {
    render: () => ({
        components: {KsForm, KsFormItem, KsInput, KsButton},
        setup() {
            const form = reactive({keyword: "", region: ""})
            return {form}
        },
        template: `
            <div style="padding:24px">
                <ks-form :model="form" inline>
                    <ks-form-item label="Namespace">
                        <ks-input v-model="form.keyword" placeholder="company.team" style="width:180px" />
                    </ks-form-item>
                    <ks-form-item label="Status">
                        <ks-input v-model="form.region" placeholder="SUCCESS" style="width:120px" />
                    </ks-form-item>
                    <ks-form-item>
                        <ks-button type="primary" native-type="submit">Search</ks-button>
                    </ks-form-item>
                </ks-form>
            </div>
        `,
    }),
}

/** Inline row – `inline` on a KsFormItem keeps the label on the left and pushes the control to the right edge */
export const InlineRow: Story = {
    render: () => ({
        components: {KsForm, KsFormItem, KsInput},
        setup() {
            const form = reactive({name: "", email: ""})
            return {form}
        },
        template: `
            <div style="padding:24px;max-width:420px">
                <ks-form :model="form" label-position="top">
                    <ks-form-item inline label="Name">
                        <ks-input v-model="form.name" style="width:220px" />
                    </ks-form-item>
                    <ks-form-item inline label="Email">
                        <ks-input v-model="form.email" style="width:220px" />
                    </ks-form-item>
                </ks-form>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const item = canvasElement.querySelector(".kel-form-item.is-inline-row")
        await expect(item).toBeTruthy()
        const styles = getComputedStyle(item as Element)
        await expect(styles.display).toBe("flex")
        await expect(styles.justifyContent).toBe("space-between")
    },
}

/** Size control – form-level size affects all children */
export const SizeControl: Story = {
    render: () => ({
        components: {KsForm, KsFormItem, KsInput, KsButton},
        setup() {
            const form = reactive({name: "", desc: ""})
            return {form}
        },
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-form :model="form" size="small" label-position="top">
                    <ks-form-item label="Name">
                        <ks-input v-model="form.name" placeholder="Flow name" />
                    </ks-form-item>
                    <ks-form-item label="Description">
                        <ks-input v-model="form.desc" type="textarea" :rows="2" />
                    </ks-form-item>
                    <ks-form-item>
                        <ks-button type="primary">Save</ks-button>
                    </ks-form-item>
                </ks-form>
            </div>
        `,
    }),
}

export const LabelPositions: Story = {
    render: () => ({
        components: {KsForm, KsFormItem, KsInput},
        setup() {
            const form = reactive({name: "", email: ""})
            return {form}
        },
        template: `
            <div style="padding:24px;max-width:400px">
                <p style="margin-bottom:8px;font-weight:600">Label Position: right</p>
                <ks-form :model="form" label-position="right" label-width="80px" style="margin-bottom:24px">
                    <ks-form-item label="Name"><ks-input v-model="form.name" /></ks-form-item>
                    <ks-form-item label="Email"><ks-input v-model="form.email" /></ks-form-item>
                </ks-form>
                <p style="margin-bottom:8px;font-weight:600">Label Position: top</p>
                <ks-form :model="form" label-position="top">
                    <ks-form-item label="Name"><ks-input v-model="form.name" /></ks-form-item>
                    <ks-form-item label="Email"><ks-input v-model="form.email" /></ks-form-item>
                </ks-form>
            </div>
        `,
    }),
}
