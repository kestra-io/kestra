import type {Meta, StoryObj} from "@storybook/vue3-vite"
import CogOutline from "vue-material-design-icons/CogOutline.vue"
import KsBreadcrumb from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"

const meta: Meta<typeof KsBreadcrumb> = {
    title: "Components/Navigation/KsBreadcrumb",
    component: KsBreadcrumb,
    tags: ["autodocs"],
}
export default meta
type Story = StoryObj<typeof KsBreadcrumb>

export const Default: Story = {
    render: (args) => ({
        components: {KsBreadcrumb},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-breadcrumb v-bind=\"args\" /></div>",
    }),
    args: {
        title: "Preferences",
        items: [{label: "Admin", onClick: () => {}}],
    },
}

export const WithIcon: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        setup() { return {CogOutline} },
        template: `
            <div style="padding:24px">
                <ks-breadcrumb
                    :items="[{label: 'Admin', onClick: () => {}}]"
                    title="Preferences"
                    :mainIcon="CogOutline"
                />
            </div>
        `,
    }),
}

export const TitleOnly: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb title="Flows" />
            </div>
        `,
    }),
}

export const Collapsed: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb
                    :items="[
                        {label: 'Admin', onClick: () => {}},
                        {label: 'IAM', onClick: () => {}},
                        {label: 'Groups', onClick: () => {}},
                        {label: 'Engineering', onClick: () => {}},
                        {label: 'Backend', onClick: () => {}},
                        {label: 'Platform', onClick: () => {}},
                    ]"
                    title="Members"
                />
            </div>
        `,
    }),
}

export const NotCollapsed: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb
                    :items="[
                        {label: 'Admin', onClick: () => {}},
                        {label: 'IAM', onClick: () => {}},
                        {label: 'Groups', onClick: () => {}},
                    ]"
                    title="Members"
                />
            </div>
        `,
    }),
}

export const NoLeading: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb
                    :items="[{label: 'Plugins', onClick: () => {}}]"
                    title="kestra-io/core"
                />
            </div>
        `,
    }),
}

export const WithLeading: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb
                    :items="[{label: 'Admin', onClick: () => {}}]"
                    title="Preferences"
                    show-leading
                />
            </div>
        `,
    }),
}

export const TitleSlot: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        template: `
            <div style="padding:24px">
                <ks-breadcrumb :items="[{label: 'Admin', onClick: () => {}}]">
                    <template #title>
                        <span>Custom <em>HTML</em> title</span>
                    </template>
                </ks-breadcrumb>
            </div>
        `,
    }),
}
