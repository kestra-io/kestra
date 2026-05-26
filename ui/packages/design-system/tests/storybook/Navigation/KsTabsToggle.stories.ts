import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import Bug from "vue-material-design-icons/Bug.vue"
import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"
import KsTabsToggle from "../../../src/components/Navigation/KsTabs/KsTabsToggle.vue"
import KsRadioButton from "../../../src/components/Form/KsRadio/KsRadioButton.vue"

const meta: Meta<typeof KsTabsToggle> = {
    title: "Components/Navigation/KsTabsToggle",
    component: KsTabsToggle,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsTabsToggle is a single-select segmented control styled like the box-style KsTabs strip. It wraps `ElRadioGroup` so it integrates with `KsForm` / `KsFormItem` validation, and accepts any number of `KsRadioButton` children."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTabsToggle>

export const Default: Story = {
    render: () => ({
        components: {KsTabsToggle, KsRadioButton},
        setup() {
            const value = ref("cat")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-tabs-toggle v-model="value">
                    <ks-radio-button value="cat">Cat</ks-radio-button>
                    <ks-radio-button value="dog">Dog</ks-radio-button>
                    <ks-radio-button value="bird">Bird</ks-radio-button>
                    <ks-radio-button value="fish">Fish</ks-radio-button>
                </ks-tabs-toggle>
                <p style="margin-top:12px;font-size:12px;opacity:0.6">Selected: {{ value }}</p>
            </div>
        `,
    }),
}

export const WithIcons: Story = {
    render: () => ({
        components: {KsTabsToggle, KsRadioButton, Bug, MessageOutline, HelpCircleOutline},
        setup() {
            const value = ref("bug")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-tabs-toggle v-model="value" aria-label="Ticket type">
                    <ks-radio-button value="bug"><Bug :size="14" />Bug</ks-radio-button>
                    <ks-radio-button value="feature"><MessageOutline :size="14" />Feature</ks-radio-button>
                    <ks-radio-button value="question"><HelpCircleOutline :size="14" />Question</ks-radio-button>
                </ks-tabs-toggle>
                <p style="margin-top:12px;font-size:12px;opacity:0.6">Selected: {{ value }}</p>
            </div>
        `,
    }),
}

export const DisabledOption: Story = {
    render: () => ({
        components: {KsTabsToggle, KsRadioButton},
        setup() { return {value: ref("a")} },
        template: `
            <div style="padding:24px">
                <ks-tabs-toggle v-model="value">
                    <ks-radio-button value="a">Option A</ks-radio-button>
                    <ks-radio-button value="b">Option B</ks-radio-button>
                    <ks-radio-button value="c" disabled>Disabled</ks-radio-button>
                </ks-tabs-toggle>
            </div>
        `,
    }),
}

export const FullyDisabled: Story = {
    render: () => ({
        components: {KsTabsToggle, KsRadioButton},
        setup() { return {value: ref("a")} },
        template: `
            <div style="padding:24px">
                <ks-tabs-toggle v-model="value" disabled>
                    <ks-radio-button value="a">Option A</ks-radio-button>
                    <ks-radio-button value="b">Option B</ks-radio-button>
                    <ks-radio-button value="c">Option C</ks-radio-button>
                </ks-tabs-toggle>
            </div>
        `,
    }),
}
