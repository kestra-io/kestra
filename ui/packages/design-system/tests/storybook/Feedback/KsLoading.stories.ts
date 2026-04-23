import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {vKsLoading} from "../../../src/components/Feedback/KsLoading"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta = {
    title: "Components/Feedback/KsLoading",
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsLoading is the Kestra design-system abstraction over `vLoading` from Element Plus. Use the `v-ks-loading` directive on any element to overlay a loading spinner. Accepts a boolean or a `LoadingOptions` object."}},
    },
}
export default meta
type Story = StoryObj

export const Default: Story = {
    render: () => ({
        directives: {KsLoading: vKsLoading},
        template: "<div v-ks-loading=\"true\" style=\"height:120px;padding:24px\"><p>Content behind the overlay.</p></div>",
    }),
}

export const NotLoading: Story = {
    render: () => ({
        directives: {KsLoading: vKsLoading},
        template: "<div v-ks-loading=\"false\" style=\"padding:24px\"><p>Loading is off — content is fully visible.</p></div>",
    }),
}

export const WithText: Story = {
    render: () => ({
        directives: {KsLoading: vKsLoading},
        template: `
            <div
                v-ks-loading="true"
                element-loading-text="Fetching data…"
                style="height:120px;padding:24px"
            >
                <p>Content behind the overlay.</p>
            </div>
        `,
    }),
}

export const WithCustomBackground: Story = {
    render: () => ({
        directives: {KsLoading: vKsLoading},
        template: `
            <div
                v-ks-loading="true"
                element-loading-background="rgba(122,122,255,0.7)"
                style="height:120px;padding:24px"
            >
                <p>Dark backdrop overlay.</p>
            </div>
        `,
    }),
}

export const ToggleLoading: Story = {
    render: () => ({
        directives: {KsLoading: vKsLoading},
        components: {KsButton},
        setup() {
            const isLoading = ref(true)
            return {isLoading}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-button @click="isLoading = !isLoading">
                    {{ isLoading ? 'Stop loading' : 'Start loading' }}
                </ks-button>
                <div v-ks-loading="isLoading" style="height:120px">
                    <p style="padding:16px">Toggle the button to control the overlay.</p>
                </div>
            </div>
        `,
    }),
}
