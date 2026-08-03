import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsDrawer from "../../../src/components/Feedback/KsDrawer.vue"
import {KsMessageBox} from "../../../src/components/Feedback/KsMessageBox"

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

/** Default – right side drawer */
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

/** Different directions */
export const Directions: Story = {
    render: () => ({
        components: {KsButton, KsDrawer},
        setup() {
            const dir = ref<string | null>(null)
            return {dir}
        },
        template: `
            <div style="padding:24px;display:flex;gap:8px;flex-wrap:wrap">
                <ks-button @click="dir = 'rtl'">Right (rtl)</ks-button>
                <ks-button @click="dir = 'ltr'">Left (ltr)</ks-button>
                <ks-button @click="dir = 'ttb'">Top (ttb)</ks-button>
                <ks-button @click="dir = 'btt'">Bottom (btt)</ks-button>
                <ks-drawer :model-value="!!dir" :direction="dir" @update:model-value="dir = null" destroy-on-close>
                    <template #header><span>Direction: {{ dir }}</span></template>
                    <p style="padding:16px">Opened from {{ dir }} direction.</p>
                </ks-drawer>
            </div>
        `,
    }),
}

/** Custom size */
export const CustomSize: Story = {
    render: () => ({
        components: {KsButton, KsDrawer},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open 400px drawer</ks-button>
                <ks-drawer v-model="visible" size="400px" destroy-on-close>
                    <template #header><span>Custom Size (400px)</span></template>
                    <p style="padding:16px">This drawer has a fixed width of 400px.</p>
                    <template #footer>
                        <ks-button type="primary" @click="visible = false">Close</ks-button>
                    </template>
                </ks-drawer>
            </div>
        `,
    }),
}

/** Resizable – drag the edge to resize (native Element Plus dragger); past 95% it counts as full screen */
export const Resizable: Story = {
    render: () => ({
        components: {KsButton, KsDrawer},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open resizable drawer</ks-button>
                <ks-drawer v-model="visible" resizable destroy-on-close>
                    <template #header><span>Resizable drawer</span></template>
                    <p style="padding:16px">Drag the edge to resize. Past 95% it is treated as full screen (the header icon flips).</p>
                </ks-drawer>
            </div>
        `,
    }),
}

/** Without header */
export const NoHeader: Story = {
    render: () => ({
        components: {KsButton, KsDrawer},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open drawer without header</ks-button>
                <ks-drawer v-model="visible" :with-header="false" destroy-on-close>
                    <div style="padding:24px">
                        <p style="margin:0 0 16px">No header bar. Manage close with your own UI.</p>
                        <ks-button type="primary" @click="visible = false">Dismiss</ks-button>
                    </div>
                </ks-drawer>
            </div>
        `,
    }),
}

/** Confirm before close – beforeClose intercepts accidental dismissal (X / Escape / overlay) */
export const ConfirmBeforeClose: Story = {
    render: () => ({
        components: {KsButton, KsDrawer},
        setup() {
            const visible = ref(false)
            const beforeClose = (done: () => void) => {
                KsMessageBox
                    .confirm("Discard your changes?", "Confirmation", {type: "warning", showCancelButton: true})
                    .then(() => done())
                    .catch(() => {})
            }
            return {visible, beforeClose}
        },
        template: `
            <div style="padding:24px">
                <ks-button type="primary" @click="visible = true">Open guarded drawer</ks-button>
                <ks-drawer v-model="visible" :before-close="beforeClose" destroy-on-close>
                    <template #header><h3>Edit something</h3></template>
                    <p style="padding:16px">Try to close with the X, Escape, or the overlay — you are asked to confirm first.</p>
                </ks-drawer>
            </div>
        `,
    }),
}
