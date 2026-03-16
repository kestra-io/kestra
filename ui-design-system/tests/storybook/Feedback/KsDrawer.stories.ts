import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsDrawer from "../../../src/components/Feedback/KsDrawer.vue"

const meta: Meta<typeof KsDrawer> = {
    title: "Components/Feedback/KsDrawer",
    component: KsDrawer,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsDrawer is the Kestra design-system abstraction over `ElDrawer` from Element Plus.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsDrawer>

export const Default: Story = {
    render: () => ({
        components: {KsButton, KsDrawer},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open Drawer</ks-button>
                <ks-drawer v-model="visible" destroy-on-close>
                    <template #header><h3>Drawer Title</h3></template>
                    <p>Drawer content goes here.</p>
                    <template #footer>
                        <ks-button @click="visible = false">Close</ks-button>
                    </template>
                </ks-drawer>
            </div>
        `,
    }),
}
