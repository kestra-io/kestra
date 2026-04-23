import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsSkeleton from "../../../src/components/Data/KsSkeleton.vue"

const meta: Meta<typeof KsSkeleton> = {
    title: "Components/Data/KsSkeleton",
    component: KsSkeleton,
    tags: ["autodocs"],
    argTypes: {
        animated: {control: "boolean"},
        loading: {control: "boolean"},
        rows: {control: {type: "number", min: 1, max: 10}},
    },
    parameters: {
        docs: {description: {component: "KsSkeleton is the Kestra design-system abstraction over `ElSkeleton` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSkeleton>

export const Default: Story = {
    render: (args) => ({
        components: {KsSkeleton},
        setup() { return {args} },
        template: "<div style=\"padding:24px;width:300px\"><ks-skeleton v-bind=\"args\" /></div>",
    }),
    args: {animated: true, rows: 3},
}

/** Configurable rows */
export const Rows: Story = {
    render: () => ({
        components: {KsSkeleton},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px;width:300px">
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 6px">2 rows</p>
                    <ks-skeleton :rows="2" />
                </div>
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 6px">5 rows</p>
                    <ks-skeleton :rows="5" />
                </div>
            </div>
        `,
    }),
}

/** Animated loading state */
export const Animated: Story = {
    render: () => ({
        components: {KsSkeleton},
        template: `
            <div style="padding:24px;width:300px">
                <ks-skeleton animated :rows="4" />
            </div>
        `,
    }),
}

/** Loading toggle – switch between skeleton and real content */
export const LoadingState: Story = {
    render: () => ({
        components: {KsSkeleton},
        setup() {
            const loading = ref(true)
            return {loading}
        },
        template: `
            <div style="padding:24px;width:360px">
                <button @click="loading = !loading" style="margin-bottom:12px">
                    Toggle loading ({{ loading ? 'loading' : 'loaded' }})
                </button>
                <ks-skeleton :loading="loading" animated :rows="3">
                    <template #default>
                        <div>
                            <h3 style="margin:0 0 8px">Flow: etl-pipeline</h3>
                            <p style="margin:0;font-size:13px;opacity:0.7">Namespace: company.data · Last run: 2 minutes ago</p>
                        </div>
                    </template>
                </ks-skeleton>
            </div>
        `,
    }),
}

export const WithContent: Story = {
    render: () => ({
        components: {KsSkeleton},
        template: `
            <div style="padding:24px;width:300px">
                <ks-skeleton :loading="true" animated :rows="4">
                    <template #default>
                        <p>Actual content loaded here</p>
                    </template>
                </ks-skeleton>
            </div>
        `,
    }),
}
