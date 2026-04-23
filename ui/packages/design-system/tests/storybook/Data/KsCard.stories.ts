import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsCard from "../../../src/components/Data/KsCard.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsCard> = {
    title: "Components/Data/KsCard",
    component: KsCard,
    tags: ["autodocs"],
    argTypes: {
        shadow: {control: "select", options: ["always", "hover", "never"]},
    },
    parameters: {
        docs: {description: {component: "KsCard is the Kestra design-system abstraction over `ElCard` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsCard>

export const Default: Story = {
    render: (args) => ({
        components: {KsCard},
        setup() { return {args} },
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-card v-bind="args">
                    <template #header><strong>Flow Details</strong></template>
                    <p>Namespace: company.team.payments</p>
                    <p>Last run: 2 hours ago</p>
                </ks-card>
            </div>
        `,
    }),
}

export const NoShadow: Story = {
    render: () => ({
        components: {KsCard},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-card shadow="never">
                    <p>Card with no shadow</p>
                </ks-card>
            </div>
        `,
    }),
}

/** Shadow variants – always, hover, never */
export const Shadow: Story = {
    render: () => ({
        components: {KsCard},
        template: `
            <div style="padding:24px;display:flex;gap:16px;flex-wrap:wrap">
                <ks-card shadow="always" style="width:180px">
                    <template #header>Always</template>
                    <p style="font-size:13px">Shadow always visible</p>
                </ks-card>
                <ks-card shadow="hover" style="width:180px">
                    <template #header>Hover</template>
                    <p style="font-size:13px">Shadow on hover only</p>
                </ks-card>
                <ks-card shadow="never" style="width:180px">
                    <template #header>Never</template>
                    <p style="font-size:13px">No shadow</p>
                </ks-card>
            </div>
        `,
    }),
}

/** Simple card – no header */
export const Simple: Story = {
    render: () => ({
        components: {KsCard},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-card shadow="never">
                    <p style="margin:0">A simple card with just body content and no header section.</p>
                </ks-card>
            </div>
        `,
    }),
}

export const WithFooter: Story = {
    render: () => ({
        components: {KsCard, KsButton},
        template: `
            <div style="padding:24px;max-width:400px">
                <ks-card shadow="never">
                    <template #header>Card with Footer</template>
                    <p>Card body content</p>
                    <template #footer>
                        <div style="display:flex;justify-content:flex-end;gap:8px">
                            <ks-button>Cancel</ks-button>
                            <ks-button type="primary">Save</ks-button>
                        </div>
                    </template>
                </ks-card>
            </div>
        `,
    }),
}
