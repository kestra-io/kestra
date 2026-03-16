import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsPagination from "../../../src/components/Data/KsPagination.vue"

const meta: Meta<typeof KsPagination> = {
    title: "Components/Data/KsPagination",
    component: KsPagination,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["small", "default", "large"]},
    },
    parameters: {
        docs: {description: {component: "KsPagination is the Kestra design-system abstraction over `ElPagination` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsPagination>

export const Default: Story = {
    render: (args) => ({
        components: {KsPagination},
        setup() {
            const currentPage = ref(1)
            const pageSize = ref(10)
            return {args, currentPage, pageSize}
        },
        template: `
            <div style="padding:24px">
                <ks-pagination
                    v-model:current-page="currentPage"
                    v-model:page-size="pageSize"
                    :total="100"
                    layout="prev, pager, next"
                    v-bind="args"
                />
                <div style="margin-top:12px;font-size:13px;opacity:0.6">Page: {{ currentPage }}</div>
            </div>
        `,
    }),
}

export const WithBackground: Story = {
    render: () => ({
        components: {KsPagination},
        setup() { return {current: ref(1)} },
        template: `
            <div style="padding:24px">
                <ks-pagination
                    v-model:current-page="current"
                    :total="200"
                    :page-size="20"
                    layout="prev, pager, next, total"
                    background
                />
            </div>
        `,
    }),
}

export const SmallSize: Story = {
    render: () => ({
        components: {KsPagination},
        setup() { return {current: ref(1)} },
        template: `
            <div style="padding:24px">
                <ks-pagination
                    v-model:current-page="current"
                    :total="50"
                    :page-size="10"
                    layout="prev, pager, next"
                    size="small"
                />
            </div>
        `,
    }),
}
