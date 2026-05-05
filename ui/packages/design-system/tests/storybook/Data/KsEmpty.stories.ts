import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsEmpty from "../../../src/components/Data/KsEmpty.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsEmpty> = {
    title: "Components/Data/KsEmpty",
    component: KsEmpty,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsEmpty is the Kestra design-system abstraction over `ElEmpty` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsEmpty>

export const Default: Story = {
    render: () => ({
        components: {KsEmpty},
        template: "<div style=\"padding:24px\"><ks-empty /></div>",
    }),
}

export const WithAction: Story = {
    render: () => ({
        components: {KsEmpty, KsButton},
        template: `
            <div style="padding:24px">
                <ks-empty description="No flows found">
                    <ks-button type="primary">Create Flow</ks-button>
                </ks-empty>
            </div>
        `,
    }),
}

/** Custom image size */
export const ImageSize: Story = {
    render: () => ({
        components: {KsEmpty},
        template: `
            <div style="padding:24px;display:flex;gap:32px;flex-wrap:wrap">
                <ks-empty :image-size="60" description="Small (60px)" />
                <ks-empty :image-size="100" description="Medium (100px)" />
                <ks-empty :image-size="160" description="Large (160px)" />
            </div>
        `,
    }),
}

/** Custom description slot */
export const CustomDescription: Story = {
    render: () => ({
        components: {KsEmpty, KsButton},
        template: `
            <div style="padding:24px">
                <ks-empty>
                    <template #description>
                        <span>No executions found. <a href="#" style="color:inherit;text-decoration:underline">Learn more</a></span>
                    </template>
                    <ks-button type="primary">Create execution</ks-button>
                </ks-empty>
            </div>
        `,
    }),
}

export const CustomImage: Story = {
    render: () => ({
        components: {KsEmpty},
        template: `
            <div style="padding:24px">
                <ks-empty :image-size="100" description="No results">
                    <template #image>
                        <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                            <circle cx="100" cy="100" r="80" fill="#f0f0f0" />
                            <text x="100" y="110" text-anchor="middle" font-size="60">📭</text>
                        </svg>
                    </template>
                </ks-empty>
            </div>
        `,
    }),
}
