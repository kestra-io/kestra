import type {Meta, StoryObj} from "@storybook/vue3-vite"
import EmptyTemplate from "./EmptyTemplate.vue"

const meta: Meta<typeof EmptyTemplate> = {
    title: "Components/Layout/EmptyTemplate",
    component: EmptyTemplate,
    tags: ["autodocs"],
    render: (args) => ({
        components: {EmptyTemplate},
        setup() {
            return {args}
        },
        template: `
            <EmptyTemplate v-bind="args">
                <h2>Nothing here yet</h2>
                <p>Create your first flow to see it listed here.</p>
            </EmptyTemplate>
        `,
    }),
}

export default meta
type Story = StoryObj<typeof EmptyTemplate>

export const Default: Story = {}

export const Plain: Story = {
    args: {plain: true},
}
