import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsMenu from "../../../src/components/Navigation/KsMenu/KsMenu.vue"
import KsMenuItem from "../../../src/components/Navigation/KsMenu/KsMenuItem.vue"

const meta: Meta<typeof KsMenu> = {
    title: "Components/Navigation/KsMenu",
    component: KsMenu,
    tags: ["autodocs"],
    argTypes: {
        mode: {control: "select", options: ["horizontal", "vertical"]},
    },
    parameters: {
        docs: {description: {component: "KsMenu is the Kestra design-system abstraction over `ElMenu` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsMenu>

export const Vertical: Story = {
    render: (args) => ({
        components: {KsMenu, KsMenuItem},
        setup() { return {args} },
        template: `
            <div style="padding:24px;width:200px">
                <ks-menu default-active="flows" v-bind="args">
                    <ks-menu-item index="flows">Flows</ks-menu-item>
                    <ks-menu-item index="executions">Executions</ks-menu-item>
                    <ks-menu-item index="namespaces">Namespaces</ks-menu-item>
                    <ks-menu-item index="settings" disabled>Settings</ks-menu-item>
                </ks-menu>
            </div>
        `,
    }),
}

/** Collapse – vertically collapsible sidebar */
export const Collapse: Story = {
    render: () => ({
        components: {KsMenu, KsMenuItem},
        setup() {
            const collapsed = ref(false)
            return {collapsed}
        },
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:flex-start">
                <button @click="collapsed = !collapsed" style="margin-top:8px">
                    {{ collapsed ? 'Expand' : 'Collapse' }}
                </button>
                <ks-menu default-active="flows" :collapse="collapsed" style="width:200px;transition:width 0.3s">
                    <ks-menu-item index="flows">Flows</ks-menu-item>
                    <ks-menu-item index="executions">Executions</ks-menu-item>
                    <ks-menu-item index="namespaces">Namespaces</ks-menu-item>
                    <ks-menu-item index="settings">Settings</ks-menu-item>
                </ks-menu>
            </div>
        `,
    }),
}

export const Horizontal: Story = {
    render: () => ({
        components: {KsMenu, KsMenuItem},
        template: `
            <div style="padding:24px">
                <ks-menu mode="horizontal" default-active="flows">
                    <ks-menu-item index="flows">Flows</ks-menu-item>
                    <ks-menu-item index="executions">Executions</ks-menu-item>
                    <ks-menu-item index="namespaces">Namespaces</ks-menu-item>
                </ks-menu>
            </div>
        `,
    }),
}
