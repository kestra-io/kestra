import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, expect} from "storybook/test"
import KsSplitter from "../../../src/components/Basic/KsSplitter/KsSplitter.vue"
import KsSplitterPanel from "../../../src/components/Basic/KsSplitter/KsSplitterPanel.vue"

const meta: Meta<typeof KsSplitter> = {
    title: "Components/Basic/KsSplitter",
    component: KsSplitter,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsSplitter is the Kestra design-system abstraction over `ElSplitter` from Element Plus. " +
                    "It divides a container into resizable panels.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsSplitter>

/** Default horizontal splitter */
export const Default: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:300px">
                <ks-splitter style="height:100%">
                    <ks-splitter-panel size="30%" min="20%">
                        <div style="padding:16px;background:var(--ks-background-body,#f5f5f5);height:100%">
                            <h4 style="margin:0 0 8px">Left Panel</h4>
                            <p style="margin:0;opacity:0.6;font-size:13px">Sidebar content</p>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <div style="padding:16px;height:100%">
                            <h4 style="margin:0 0 8px">Right Panel</h4>
                            <p style="margin:0;opacity:0.6;font-size:13px">Main content area</p>
                        </div>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("Left Panel")).toBeTruthy()
        await expect(canvas.getByText("Right Panel")).toBeTruthy()
    },
}

/** Vertical splitter */
export const Vertical: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:400px">
                <ks-splitter layout="vertical" style="height:100%">
                    <ks-splitter-panel size="40%" min="20%">
                        <div style="padding:16px;height:100%">
                            <h4 style="margin:0 0 8px">Top Panel</h4>
                            <p style="margin:0;opacity:0.6;font-size:13px">Upper content area</p>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <div style="padding:16px;height:100%">
                            <h4 style="margin:0 0 8px">Bottom Panel</h4>
                            <p style="margin:0;opacity:0.6;font-size:13px">Lower content area</p>
                        </div>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
}

/** Three-panel layout */
export const ThreePanels: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:300px">
                <ks-splitter style="height:100%">
                    <ks-splitter-panel size="20%" min="15%">
                        <div style="padding:12px;height:100%;font-size:13px">
                            <strong>Navigator</strong>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <div style="padding:12px;height:100%;font-size:13px">
                            <strong>Editor</strong>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel size="25%" min="15%">
                        <div style="padding:12px;height:100%;font-size:13px">
                            <strong>Properties</strong>
                        </div>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
}

/** Size constraints – panels respect min and max */
export const SizeConstraints: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:280px">
                <ks-splitter style="height:100%">
                    <ks-splitter-panel size="40%" min="25%" max="60%">
                        <div style="padding:16px;height:100%">
                            <p style="margin:0;font-size:13px">Min 25% · Max 60%</p>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <div style="padding:16px;height:100%">
                            <p style="margin:0;font-size:13px">Takes remaining space</p>
                        </div>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
}

/** Non-resizable panel */
export const NonResizable: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:280px">
                <ks-splitter style="height:100%">
                    <ks-splitter-panel size="240px" :resizable="false">
                        <div style="padding:16px;height:100%">
                            <p style="margin:0;font-size:13px;opacity:0.6">Fixed sidebar (240px, non-resizable)</p>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <div style="padding:16px;height:100%">
                            <p style="margin:0;font-size:13px">Flexible main area</p>
                        </div>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
}

/** Nested splitters */
export const Nested: Story = {
    render: () => ({
        components: {KsSplitter, KsSplitterPanel},
        template: `
            <div style="padding:24px;height:360px">
                <ks-splitter style="height:100%">
                    <ks-splitter-panel size="25%" min="15%">
                        <div style="padding:12px;height:100%">
                            <strong style="font-size:13px">Sidebar</strong>
                        </div>
                    </ks-splitter-panel>
                    <ks-splitter-panel>
                        <ks-splitter layout="vertical" style="height:100%">
                            <ks-splitter-panel size="60%">
                                <div style="padding:12px;height:100%">
                                    <strong style="font-size:13px">Editor</strong>
                                </div>
                            </ks-splitter-panel>
                            <ks-splitter-panel>
                                <div style="padding:12px;height:100%">
                                    <strong style="font-size:13px">Terminal</strong>
                                </div>
                            </ks-splitter-panel>
                        </ks-splitter>
                    </ks-splitter-panel>
                </ks-splitter>
            </div>
        `,
    }),
}
