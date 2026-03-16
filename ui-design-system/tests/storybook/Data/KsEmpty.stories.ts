import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsEmpty from "../../../src/components/Data/KsEmpty.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsEmpty> = {
    title: "Components/Data/KsEmpty",
    component: KsEmpty,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsEmpty is the Kestra design-system abstraction over `ElEmpty` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsEmpty>

export const Default: Story = {
    render: (args) => ({
        components: {KsEmpty},
        setup() { return {args} },
        template: `<div style="padding:24px"><ks-empty v-bind="args" /></div>`,
    }),
    args: {description: "No data available"},
}

export const WithAction: Story = {
    render: () => ({
        components: {KsEmpty, KsButton},
        template: `
            <div style="padding:24px">
                <ks-empty description="No flows found">
                    <ks-button type="primary">Create Flow</ks-button>
                </ks-empty>
            </div>
        `,
    }),
}

export const CustomImage: Story = {
    render: () => ({
        components: {KsEmpty},
        template: `
            <div style="padding:24px">
                <ks-empty :image-size="100" description="No results">
                    <template #image>
                        <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                            <circle cx="100" cy="100" r="80" fill="#f0f0f0" />
                            <text x="100" y="110" text-anchor="middle" font-size="60">📭</text>
                        </svg>
                    </template>
                </ks-empty>
            </div>
        `,
    }),
}
