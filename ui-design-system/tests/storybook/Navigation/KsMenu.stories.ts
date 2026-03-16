import type {Meta, StoryObj} from "@storybook/vue3-vite"
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
