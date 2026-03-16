import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsSplitter from "../../../src/components/Basic/KsSplitter/KsSplitter.vue"
import KsSplitterPanel from "../../../src/components/Basic/KsSplitter/KsSplitterPanel.vue"

const meta: Meta<typeof KsSplitter> = {
    title: "Components/Basic/KsSplitter",
    component: KsSplitter,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsSplitter is the Kestra design-system abstraction over `ElSplitter` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSplitter>

export const Default: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:300px">
                <ks-splitter style="height:100%">
                    <ks-splitter-panel size="30%" min="20%">
                        <div style="padding:16px;background:#f5f5f5;height:100%">
                            <h4>Left Panel</h4>
                            <p>Sidebar content</p>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <div style="padding:16px;height:100%">
                            <h4>Right Panel</h4>
                            <p>Main content area</p>
                        </div>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
}
