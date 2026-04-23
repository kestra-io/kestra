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

/** Basic usage – click trigger */
export const Default: Story = {
    render: () => ({
        components: {KsButton, KsPopover},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:48px">
                <ks-popover title="This is title" placement="bottom" :width="200" trigger="click" v-model:visible="visible">
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

/** Trigger methods – hover, click, focus */
export const Triggers: Story = {
    render: () => ({
        components: {KsButton, KsPopover},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:48px;display:flex;gap:16px;flex-wrap:wrap">
                <ks-popover
                    placement="top-start"
                    title="Title"
                    :width="200"
                    trigger="hover"
                    content="this is content, this is content, this is content"
                >
                    <template #reference>
                        <ks-button>Hover to activate</ks-button>
                    </template>
                </ks-popover>

                <ks-popover
                    placement="bottom"
                    title="Title"
                    :width="200"
                    trigger="click"
                    content="this is content, this is content, this is content"
                >
                    <template #reference>
                        <ks-button>Click to activate</ks-button>
                    </template>
                </ks-popover>

                <ks-popover
                    placement="right"
                    title="Title"
                    :width="200"
                    trigger="focus"
                    content="this is content, this is content, this is content"
                >
                    <template #reference>
                        <ks-button>Focus to activate</ks-button>
                    </template>
                </ks-popover>

                <ks-popover
                    title="Title"
                    :width="200"
                    trigger="contextmenu"
                    content="this is content, this is content, this is content"
                >
                    <template #reference>
                        <ks-button>contextmenu to activate</ks-button>
                    </template>
                </ks-popover>

                <ks-popover
                    :visible="visible"
                    placement="bottom"
                    title="Title"
                    :width="200"
                    content="this is content, this is content, this is content"
                >
                    <template #reference>
                        <ks-button @click="visible = !visible">Manual to activate</ks-button>
                    </template>
                </ks-popover>
            </div>
        `,
    }),
}

/** Theme – dark and light */
export const Theme: Story = {
    render: () => ({
        components: {KsButton, KsPopover},
        template: `
            <div style="padding:48px;display:flex;gap:16px">
                <ks-popover placement="top" trigger="hover" title="This is title" :width="160" effect="dark">
                    <p style="margin:0">This is dark</p>
                    <template #reference><ks-button>Dark</ks-button></template>
                </ks-popover>

                <ks-popover placement="top" trigger="hover" title="This is title" :width="160" effect="light">
                    <p style="margin:0">This is light</p>
                    <template #reference><ks-button>Light</ks-button></template>
                </ks-popover>
            </div>
        `,
    }),
}

/** Placements – all directions */
export const Placements: Story = {
    render: () => ({
        components: {KsButton, KsPopover},
        template: `
            <div style="padding:64px;display:flex;flex-wrap:wrap;gap:8px;max-width:400px">
                <template v-for="p in ['top-start','top','top-end','left','right','bottom-start','bottom','bottom-end']" :key="p">
                    <ks-popover :placement="p" :width="120" trigger="hover">
                        <p style="margin:0;font-size:12px">{{ p }}</p>
                        <template #reference>
                            <ks-button size="small">{{ p }}</ks-button>
                        </template>
                    </ks-popover>
                </template>
            </div>
        `,
    }),
}

/** Rich content – nested elements */
export const RichContent: Story = {
    render: () => ({
        components: {KsButton, KsPopover},
        setup() {
            const visible = ref(false)
            return {visible}
        },
        template: `
            <div style="padding:48px">
                <ks-popover :width="280" v-model:visible="visible">
                    <div>
                        <p style="font-weight:600;margin:0 0 8px">Flow: etl-pipeline</p>
                        <p style="font-size:13px;opacity:0.7;margin:0 0 4px">Namespace: company.data</p>
                        <p style="font-size:13px;opacity:0.7;margin:0 0 12px">Last run: 2 minutes ago</p>
                        <ks-button size="small" type="primary" @click="visible = false">View details</ks-button>
                    </div>
                    <template #reference>
                        <ks-button type="primary">Flow info</ks-button>
                    </template>
                </ks-popover>
            </div>
        `,
    }),
}
