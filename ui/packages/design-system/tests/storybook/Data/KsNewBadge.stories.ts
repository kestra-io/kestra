import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsNewBadge from "../../../src/components/Data/KsNewBadge.vue"

const meta: Meta<typeof KsNewBadge> = {
    title: "Components/Data/KsNewBadge",
    component: KsNewBadge,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsNewBadge is a compact uppercase pill that flags a newly shipped feature (e.g. next to a sidebar nav item). The label is controlled by the caller via the default slot — wire it to your own i18n; it defaults to \"NEW\". Styling is uppercased via CSS, so any cased input renders uppercase.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsNewBadge>

export const Default: Story = {
    render: () => ({
        components: {KsNewBadge},
        template: "<div style=\"padding:24px\"><ks-new-badge /></div>",
    }),
}

export const CustomLabel: Story = {
    render: () => ({
        components: {KsNewBadge},
        template: "<div style=\"padding:24px\"><ks-new-badge>Nouveau</ks-new-badge></div>",
    }),
    parameters: {
        docs: {description: {story: "The default slot overrides the label — pass a localized string such as `t(\"new\")`. CSS uppercasing keeps the visual consistent regardless of input casing."}},
    },
}

export const NextToLabel: Story = {
    render: () => ({
        components: {KsNewBadge},
        template: `
            <div style="padding:24px;display:flex;align-items:center;gap:8px">
                <span>MCP Servers</span>
                <ks-new-badge>New</ks-new-badge>
            </div>
        `,
    }),
}
