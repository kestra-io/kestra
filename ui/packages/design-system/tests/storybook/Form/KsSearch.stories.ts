import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsSearch from "../../../src/components/Form/KsSearch.vue"

const meta: Meta<typeof KsSearch> = {
    title: "Components/Form/KsSearch",
    component: KsSearch,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        clearable: {control: "boolean"},
        readonly: {control: "boolean"},
        placeholder: {control: "text"},
    },
    parameters: {
        docs: {description: {component: "KsSearch is the Kestra design-system search input. It wraps `ElInput` with a built-in Magnify prefix icon and the standard search-field styling (border, elevation, focus ring). Use it anywhere the UI needs a single-line text search."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSearch>

export const Default: Story = {
    render: (args) => ({
        components: {KsSearch},
        setup() {
            const value = ref("")
            return {args, value}
        },
        template: "<div style=\"padding:24px;width:300px\"><ks-search v-model=\"value\" v-bind=\"args\" /></div>",
    }),
    args: {placeholder: "Search..."},
}

export const Clearable: Story = {
    render: () => ({
        components: {KsSearch},
        setup() { return {value: ref("Clear me")} },
        template: "<div style=\"padding:24px;width:300px\"><ks-search v-model=\"value\" clearable placeholder=\"Search...\" /></div>",
    }),
}

export const WithSuffix: Story = {
    render: () => ({
        components: {KsSearch},
        setup() { return {value: ref("")} },
        template: `
            <div style="padding:24px;width:300px">
                <ks-search v-model="value" placeholder="Jump to...">
                    <template #suffix><kbd>ESC</kbd></template>
                </ks-search>
            </div>
        `,
    }),
}

export const CustomPrefix: Story = {
    render: () => ({
        components: {KsSearch},
        setup() { return {value: ref("")} },
        template: `
            <div style="padding:24px;width:300px">
                <ks-search v-model="value" placeholder="Filter">
                    <template #prefix>#</template>
                </ks-search>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsSearch},
        setup() { return {value: ref("Disabled value")} },
        template: "<div style=\"padding:24px;width:300px\"><ks-search v-model=\"value\" disabled placeholder=\"Search...\" /></div>",
    }),
}
