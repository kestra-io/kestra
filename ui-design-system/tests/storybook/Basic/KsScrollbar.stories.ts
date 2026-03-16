import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsScrollbar from "../../../src/components/Basic/KsScrollbar.vue"

const meta: Meta<typeof KsScrollbar> = {
    title: "Components/Basic/KsScrollbar",
    component: KsScrollbar,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsScrollbar is the Kestra design-system abstraction over `ElScrollbar` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsScrollbar>

export const Default: Story = {
    render: () => ({
        components: {KsScrollbar},
        template: `
            <div style="padding:24px">
                <ks-scrollbar max-height="200px">
                    <div v-for="i in 20" :key="i" style="padding:8px;border-bottom:1px solid #eee">
                        Item {{ i }}
                    </div>
                </ks-scrollbar>
            </div>
        `,
    }),
}
