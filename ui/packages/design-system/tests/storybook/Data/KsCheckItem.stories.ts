import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsCheckItem from "../../../src/components/Data/KsCheckItem.vue"

const meta: Meta<typeof KsCheckItem> = {
    title: "Components/Data/KsCheckItem",
    component: KsCheckItem,
    tags: ["autodocs"],
    argTypes: {
        met: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "A single requirement / checklist row: a circle that turns into a green check when `met`."}},
    },
}
export default meta
type Story = StoryObj<typeof KsCheckItem>

export const Pending: Story = {
    render: () => ({
        components: {KsCheckItem},
        template: "<ks-check-item :met=\"false\">At least 8 characters</ks-check-item>",
    }),
}

export const Met: Story = {
    render: () => ({
        components: {KsCheckItem},
        template: "<ks-check-item :met=\"true\">Passwords match</ks-check-item>",
    }),
}

export const LongLabel: Story = {
    render: () => ({
        components: {KsCheckItem},
        template: `
            <div style="max-width:220px">
                <ks-check-item :met="false">One special character (!@#$%^&amp;*) and at least twelve characters long</ks-check-item>
            </div>
        `,
    }),
}
