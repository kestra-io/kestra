import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsSkeleton from "../../../src/components/Data/KsSkeleton.vue"

const meta: Meta<typeof KsSkeleton> = {
    title: "Components/Data/KsSkeleton",
    component: KsSkeleton,
    tags: ["autodocs"],
    argTypes: {
        animated: {control: "boolean"},
        loading: {control: "boolean"},
        rows: {control: {type: "number", min: 1, max: 10}},
    },
    parameters: {
        docs: {description: {component: "KsSkeleton is the Kestra design-system abstraction over `ElSkeleton` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSkeleton>

export const Default: Story = {
    render: (args) => ({
        components: {KsSkeleton},
        setup() { return {args} },
        template: `<div style="padding:24px;width:300px"><ks-skeleton v-bind="args" /></div>`,
    }),
    args: {animated: true, rows: 3},
}

export const WithContent: Story = {
    render: () => ({
        components: {KsSkeleton},
        template: `
            <div style="padding:24px;width:300px">
                <ks-skeleton :loading="true" animated :rows="4">
                    <template #default>
                        <p>Actual content loaded here</p>
                    </template>
                </ks-skeleton>
            </div>
        `,
    }),
}
