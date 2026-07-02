import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsSideBar from "../../../src/components/Navigation/KsSideBar/KsSideBar.vue"
import KsSideBarItem from "../../../src/components/Navigation/KsSideBar/KsSideBarItem.vue"
import KsSideBarSection from "../../../src/components/Navigation/KsSideBar/KsSideBarSection.vue"
import KsNewBadge from "../../../src/components/Data/KsNewBadge.vue"
import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
import PlayOutline from "vue-material-design-icons/PlayOutline.vue"
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
import PuzzleOutline from "vue-material-design-icons/PuzzleOutline.vue"

const meta: Meta<typeof KsSideBar> = {
    title: "Components/Navigation/KsSideBar",
    component: KsSideBar,
    tags: ["autodocs"],
    parameters: {
        layout: "fullscreen",
        docs: {
            description: {
                component: "Slot-driven sidebar shell with header / scrollable body / footer regions. Compose with `KsSideBarSection` and `KsSideBarItem` to build a left navigation. Item routing is the consumer's responsibility — wrap items in `<router-link custom>` and bind `href` + `@click=\"navigate\"` to integrate with vue-router.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsSideBar>

const wrapperStyle = "height:600px;width:240px;border:1px solid var(--ks-border-default);"

export const Default: Story = {
    render: () => ({
        components: {KsSideBar, KsSideBarSection, KsSideBarItem, ChartLineVariant, FileTreeOutline, PlayOutline, FileDocumentOutline, FolderOpenOutline, PuzzleOutline},
        setup() {
            return {ChartLineVariant, FileTreeOutline, PlayOutline, FileDocumentOutline, FolderOpenOutline, PuzzleOutline}
        },
        template: `
            <div style="${wrapperStyle}">
                <ks-side-bar>
                    <ks-side-bar-section title="Workspace" collapsible>
                        <ks-side-bar-item title="Dashboards" :icon="ChartLineVariant" href="/dashboards" active />
                        <ks-side-bar-item title="Flows" :icon="FileTreeOutline" href="/flows" />
                        <ks-side-bar-item title="Executions" :icon="PlayOutline" href="/executions" />
                        <ks-side-bar-item title="Logs" :icon="FileDocumentOutline" href="/logs" />
                    </ks-side-bar-section>
                    <ks-side-bar-section title="Resources" collapsible>
                        <ks-side-bar-item title="Namespaces" :icon="FolderOpenOutline" href="/namespaces" />
                        <ks-side-bar-item title="Plugins" :icon="PuzzleOutline" href="/plugins" locked />
                    </ks-side-bar-section>
                </ks-side-bar>
            </div>
        `,
    }),
}

export const CollapsedSection: Story = {
    render: () => ({
        components: {KsSideBar, KsSideBarSection, KsSideBarItem, ChartLineVariant, FileTreeOutline, FolderOpenOutline},
        setup() {
            return {ChartLineVariant, FileTreeOutline, FolderOpenOutline}
        },
        template: `
            <div style="${wrapperStyle}">
                <ks-side-bar>
                    <ks-side-bar-section title="Workspace" collapsible>
                        <ks-side-bar-item title="Dashboards" :icon="ChartLineVariant" href="/dashboards" />
                        <ks-side-bar-item title="Flows" :icon="FileTreeOutline" href="/flows" />
                    </ks-side-bar-section>
                    <ks-side-bar-section title="Resources" collapsible default-collapsed>
                        <ks-side-bar-item title="Namespaces" :icon="FolderOpenOutline" href="/namespaces" />
                    </ks-side-bar-section>
                </ks-side-bar>
            </div>
        `,
    }),
}

export const WithHeaderAndFooter: Story = {
    render: () => ({
        components: {KsSideBar, KsSideBarSection, KsSideBarItem, ChartLineVariant, FileTreeOutline},
        setup() {
            return {ChartLineVariant, FileTreeOutline}
        },
        template: `
            <div style="${wrapperStyle}">
                <ks-side-bar>
                    <template #header>
                        <div style="font-weight:600;padding-bottom:1rem;">My App</div>
                    </template>
                    <ks-side-bar-section title="Workspace">
                        <ks-side-bar-item title="Dashboards" :icon="ChartLineVariant" href="/dashboards" active />
                        <ks-side-bar-item title="Flows" :icon="FileTreeOutline" href="/flows" />
                    </ks-side-bar-section>
                    <template #footer>
                        <div style="padding:1rem;border-top:1px solid var(--ks-border-default);">user@example.com</div>
                    </template>
                </ks-side-bar>
            </div>
        `,
    }),
}

export const States: Story = {
    render: () => ({
        components: {KsSideBar, KsSideBarSection, KsSideBarItem, FileTreeOutline},
        setup() {
            return {FileTreeOutline}
        },
        template: `
            <div style="${wrapperStyle}">
                <ks-side-bar>
                    <ks-side-bar-section title="States">
                        <ks-side-bar-item title="Default" :icon="FileTreeOutline" href="#" />
                        <ks-side-bar-item title="Active" :icon="FileTreeOutline" href="#" active />
                        <ks-side-bar-item title="Locked (EE)" :icon="FileTreeOutline" href="#" locked />
                        <ks-side-bar-item title="Active + Locked" :icon="FileTreeOutline" href="#" active locked />
                    </ks-side-bar-section>
                </ks-side-bar>
            </div>
        `,
    }),
}

export const LongLabelTruncates: Story = {
    render: () => ({
        components: {KsSideBar, KsSideBarSection, KsSideBarItem, FileTreeOutline},
        setup() {
            return {FileTreeOutline}
        },
        template: `
            <div style="${wrapperStyle}">
                <ks-side-bar>
                    <ks-side-bar-section title="Overflow">
                        <ks-side-bar-item title="A very long item label that should ellipsize gracefully without breaking layout" :icon="FileTreeOutline" href="#" />
                    </ks-side-bar-section>
                </ks-side-bar>
            </div>
        `,
    }),
}

export const NewFeatureSpotlight: Story = {
    render: () => ({
        components: {KsSideBar, KsSideBarSection, KsSideBarItem, KsNewBadge, ChartLineVariant, FileTreeOutline, FolderOpenOutline, PuzzleOutline},
        setup() {
            return {ChartLineVariant, FileTreeOutline, FolderOpenOutline, PuzzleOutline}
        },
        template: `
            <div style="${wrapperStyle}">
                <ks-side-bar>
                    <ks-side-bar-section title="Workspace" collapsible>
                        <ks-side-bar-item title="Dashboards" :icon="ChartLineVariant" href="/dashboards" active />
                        <ks-side-bar-item title="Flows" :icon="FileTreeOutline" href="/flows" />
                    </ks-side-bar-section>
                    <ks-side-bar-section title="Resources" collapsible>
                        <template #suffix>
                            <ks-new-badge>New</ks-new-badge>
                        </template>
                        <ks-side-bar-item title="Namespaces" :icon="FolderOpenOutline" href="/namespaces" />
                        <ks-side-bar-item title="Plugins" :icon="PuzzleOutline" href="/plugins">
                            <template #suffix>
                                <ks-new-badge>New</ks-new-badge>
                            </template>
                        </ks-side-bar-item>
                    </ks-side-bar-section>
                </ks-side-bar>
            </div>
        `,
    }),
}
