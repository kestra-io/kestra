import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsBreadcrumb from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"
import KsBreadcrumbItem from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumbItem.vue"

const meta: Meta<typeof KsBreadcrumb> = {
    title: "Components/Navigation/KsBreadcrumb",
    component: KsBreadcrumb,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsBreadcrumb is the Kestra design-system abstraction over `ElBreadcrumb` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsBreadcrumb>

export const Default: Story = {
    render: () => ({
        components: {KsBreadcrumb, KsBreadcrumbItem},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb separator="/">
                    <ks-breadcrumb-item>Home</ks-breadcrumb-item>
                    <ks-breadcrumb-item>Flows</ks-breadcrumb-item>
                    <ks-breadcrumb-item>my-flow</ks-breadcrumb-item>
                </ks-breadcrumb>
            </div>
        `,
    }),
}

export const CustomSeparator: Story = {
    render: () => ({
        components: {KsBreadcrumb, KsBreadcrumbItem},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb separator=">">
                    <ks-breadcrumb-item>Namespaces</ks-breadcrumb-item>
                    <ks-breadcrumb-item>company.team</ks-breadcrumb-item>
                    <ks-breadcrumb-item>Flows</ks-breadcrumb-item>
                </ks-breadcrumb>
            </div>
        `,
    }),
}
