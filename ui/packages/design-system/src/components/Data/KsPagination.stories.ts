import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsPagination from "./KsPagination.vue"

const meta: Meta<typeof KsPagination> = {
    title: "Components/Data/KsPagination",
    component: KsPagination,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["small", "default", "large"]},
        background: {control: "boolean", type: {name: "boolean"}},
        disabled: {control: "boolean"},
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
            const currentPage = ref(args.currentPage ?? 1)
            const pageSize = ref(args.pageSize ?? 10)
            return {args, currentPage, pageSize}
        },
        template: `
            <div style="padding:24px">
                <ks-pagination
                    v-bind="args"
                    v-model:current-page="currentPage"
                    v-model:page-size="pageSize"
                />
                <div style="margin-top:12px;font-size:13px;opacity:0.6">Page: {{ currentPage }}</div>
            </div>
        `,
    }),
    args: {
        total: 100,
        currentPage: 1,
        pageSize: 10,
        pagerCount: 7,
        layout: "prev, pager, next",
        size: "default",
        background: false,
        disabled: false,
    },
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
    parameters: {controls: {disable: true}},
}

/** Number of pagers – controls visible page buttons */
export const PagerCount: Story = {
    render: () => ({
        components: {KsPagination},
        setup() { return {current: ref(5)} },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 6px">pager-count: 5 (default)</p>
                    <ks-pagination v-model:current-page="current" :total="100" :page-size="10" layout="prev, pager, next" />
                </div>
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 6px">pager-count: 3</p>
                    <ks-pagination v-model:current-page="current" :total="100" :page-size="10" layout="prev, pager, next" :pager-count="5" />
                </div>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}

/** Full layout – sizes, prev, pager, next, jumper, total */
export const FullLayout: Story = {
    render: () => ({
        components: {KsPagination},
        setup() {
            const current = ref(1)
            const pageSize = ref(10)
            return {current, pageSize}
        },
        template: `
            <div style="padding:24px">
                <ks-pagination
                    v-model:current-page="current"
                    v-model:page-size="pageSize"
                    :total="400"
                    layout="total, sizes, prev, pager, next, jumper"
                    background
                />
                <div style="margin-top:8px;font-size:13px;opacity:0.6">
                    Page {{ current }}, {{ pageSize }} per page
                </div>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
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
    parameters: {controls: {disable: true}},
}
