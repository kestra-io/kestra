import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsCheckbox from "../../../src/components/Form/KsCheckbox/KsCheckbox.vue"
import KsCheckboxGroup from "../../../src/components/Form/KsCheckbox/KsCheckboxGroup.vue"
import KsCheckboxButton from "../../../src/components/Form/KsCheckbox/KsCheckboxButton.vue"

const meta: Meta<typeof KsCheckbox> = {
    title: "Components/Form/KsCheckbox",
    component: KsCheckbox,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsCheckbox is the Kestra design-system abstraction over `ElCheckbox` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsCheckbox>

export const Default: Story = {
    render: (args) => ({
        components: {KsCheckbox},
        setup() {
            const value = ref(false)
            return {args, value}
        },
        template: `
            <div style="padding:24px">
                <ks-checkbox v-model="value" v-bind="args">Check me</ks-checkbox>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const Group: Story = {
    render: () => ({
        components: {KsCheckboxGroup, KsCheckbox},
        setup() {
            const value = ref<string[]>([])
            const options = ["Option A", "Option B", "Option C", "Option D"]
            return {value, options}
        },
        template: `
            <div style="padding:24px">
                <ks-checkbox-group v-model="value">
                    <ks-checkbox v-for="opt in options" :key="opt" :value="opt">{{ opt }}</ks-checkbox>
                </ks-checkbox-group>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value.join(', ') || '(none)' }}</span>
            </div>
        `,
    }),
}

export const ButtonGroup: Story = {
    render: () => ({
        components: {KsCheckboxGroup, KsCheckboxButton},
        setup() {
            const value = ref<string[]>(["B"])
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-checkbox-group v-model="value">
                    <ks-checkbox-button value="A">Alpha</ks-checkbox-button>
                    <ks-checkbox-button value="B">Beta</ks-checkbox-button>
                    <ks-checkbox-button value="C">Gamma</ks-checkbox-button>
                </ks-checkbox-group>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value.join(', ') || '(none)' }}</span>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsCheckbox},
        setup() { return {value: ref(true)} },
        template: "<div style=\"padding:24px\"><ks-checkbox v-model=\"value\" disabled>Disabled</ks-checkbox></div>",
    }),
}

export const Indeterminate: Story = {
    render: () => ({
        components: {KsCheckbox, KsCheckboxGroup},
        setup() {
            const checkAll = ref(false)
            const indeterminate = ref(true)
            const checkedCities = ref(["Shanghai", "Beijing"])
            const cities = ["Shanghai", "Beijing", "Guangzhou", "Shenzhen"]

            function handleCheckAllChange(val: boolean) {
                checkedCities.value = val ? [...cities] : []
                indeterminate.value = false
            }

            function handleCheckedChange(value: string[]) {
                const count = value.length
                checkAll.value = count === cities.length
                indeterminate.value = count > 0 && count < cities.length
            }

            return {checkAll, indeterminate, checkedCities, cities, handleCheckAllChange, handleCheckedChange}
        },
        template: `
            <div style="padding:24px">
                <ks-checkbox v-model="checkAll" :indeterminate="indeterminate" @change="handleCheckAllChange">Check all</ks-checkbox>
                <div style="margin:8px 0;border-top:1px solid #ccc;padding-top:8px">
                    <ks-checkbox-group v-model="checkedCities" @change="handleCheckedChange">
                        <ks-checkbox v-for="city in cities" :key="city" :value="city">{{ city }}</ks-checkbox>
                    </ks-checkbox-group>
                </div>
            </div>
        `,
    }),
}
