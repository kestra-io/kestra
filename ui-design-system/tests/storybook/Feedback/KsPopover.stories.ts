import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsPopover from "../../../src/components/Feedback/KsPopover.vue"

const meta: Meta<typeof KsPopover> = {
    title: "Components/Feedback/KsPopover",
    component: KsPopover,
    tags: ["autodocs"],
    argTypes: {
        placement: {control: "select", options: ["top", "bottom", "left", "right", "bottom-start", "bottom-end"]},
        trigger: {control: "select", options: ["hover", "click", "focus"]},
        showArrow: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component: "KsPopover is the Kestra design-system abstraction over `ElPopover` from Element Plus.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsPopover>

export const Default: Story = {
    render: () => ({
        components: {KsButton, KsPopover},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:48px">
                <ks-popover placement="bottom" :width="200" trigger="click" v-model:visible="visible">
                    <p>This is popover content.</p>
                    <ks-button size="small" @click="visible = false">Close</ks-button>
                    <template #reference>
                        <ks-button type="primary">Click me</ks-button>
                    </template>
                </ks-popover>
            </div>
        `,
    }),
}
