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
        setup() {
            const value = ref("")
            return () => (
                <div style="padding:24px;width:300px">
                    <KsSearch v-model={value.value} {...args} />
                </div>
            )
        },
    }),
    args: {placeholder: "Search..."},
}

export const Clearable: Story = {
    render: () => ({
        setup() {
            const value = ref("Clear me")
            return () => (
                <div style="padding:24px;width:300px">
                    <KsSearch v-model={value.value} clearable placeholder="Search..." />
                </div>
            )
        },
    }),
}

export const WithSuffix: Story = {
    render: () => ({
        setup() {
            const value = ref("")
            return () => (
                <div style="padding:24px;width:300px">
                    <KsSearch v-model={value.value} placeholder="Jump to...">
                        {{suffix: () => <kbd>ESC</kbd>}}
                    </KsSearch>
                </div>
            )
        },
    }),
}

export const CustomPrefix: Story = {
    render: () => ({
        setup() {
            const value = ref("")
            return () => (
                <div style="padding:24px;width:300px">
                    <KsSearch v-model={value.value} placeholder="Filter">
                        {{prefix: () => "#"}}
                    </KsSearch>
                </div>
            )
        },
    }),
}

export const Disabled: Story = {
    render: () => ({
        setup() {
            const value = ref("Disabled value")
            return () => (
                <div style="padding:24px;width:300px">
                    <KsSearch v-model={value.value} disabled placeholder="Search..." />
                </div>
            )
        },
    }),
}
