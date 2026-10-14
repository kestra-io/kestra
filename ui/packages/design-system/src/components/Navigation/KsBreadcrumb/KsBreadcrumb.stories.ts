import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, screen, userEvent, waitFor, within} from "storybook/test"
import CogOutline from "vue-material-design-icons/CogOutline.vue"
import FolderOutline from "vue-material-design-icons/FolderOutline.vue"
import KsBreadcrumb from "./KsBreadcrumb.vue"
import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "./types"

const meta: Meta<typeof KsBreadcrumb> = {
    title: "Components/Navigation/KsBreadcrumb",
    component: KsBreadcrumb,
    tags: ["autodocs"],
}
export default meta
type Story = StoryObj<typeof KsBreadcrumb>

const NAMESPACES: Record<string, string[]> = {
    "": ["system", "company", "personal", "test"],
    "company": ["team", "marketing", "finance"],
    "company.finance": ["invoicing", "reporting"],
    "company.finance.reporting": ["monthly", "quarterly"],
}

const after = <T,>(value: T, delay: number) => new Promise<T>((resolve) => setTimeout(() => resolve(value), delay))

const contentOf = (parent: string, current?: string, delay = 300): KsBreadcrumbLoader => () => after(
    (NAMESPACES[parent] ?? []).map((name) => {
        const id = parent ? `${parent}.${name}` : name
        return {
            label: name,
            onClick: () => {},
            icon: FolderOutline,
            tooltip: id,
            current: id === current,
            children: NAMESPACES[id] ? contentOf(id, current, delay) : undefined,
        }
    }),
    delay,
)

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

/**
 * A folder-style path. The root offers what lies under it, every other level the entries beside it, and
 * hovering an entry with content flies out its own namespaces and flows, as deep as the tree goes.
 */
export const WithSiblings: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        setup() {
            const items: KsBreadcrumbItem[] = [{label: "Namespaces", onClick: () => {}, children: contentOf("", "company")}]
            return {items, titleSiblings: contentOf("", "company")}
        },
        template: `
            <div style="padding:24px 24px 320px">
                <ks-breadcrumb :items="items" title="company" :titleSiblings="titleSiblings" show-leading />
            </div>
        `,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        const entry = (label: string) => screen.getAllByTestId("breadcrumb-entry").find((element) => element.textContent?.trim() === label)
        await waitFor(() => expect(canvas.getAllByTestId("breadcrumb-menu-trigger")).toHaveLength(2))
        await userEvent.hover(within(canvas.getByRole("heading")).getByTestId("breadcrumb-segment"))
        await waitFor(() => expect(entry("personal")).toBeVisible())
        await userEvent.hover(entry("company")!)
        await waitFor(() => expect(entry("finance")).toBeVisible())
        await userEvent.hover(entry("finance")!)
        await waitFor(() => expect(entry("reporting")).toBeVisible())
    },
}

/** A level with nothing to offer gets no chevron: here only the root has one, the current page does not. */
export const EmptySiblings: Story = {
    render: () => ({
        components: {KsBreadcrumb},
        setup() {
            const items: KsBreadcrumbItem[] = [{label: "Namespaces", onClick: () => {}, children: contentOf("", undefined, 0)}]
            return {items, titleSiblings: () => Promise.resolve([])}
        },
        template: `
            <div style="padding:24px">
                <ks-breadcrumb :items="items" title="only-namespace" :titleSiblings="titleSiblings" />
            </div>
        `,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getAllByTestId("breadcrumb-menu-trigger")).toHaveLength(1))
        await expect(within(canvas.getByRole("heading")).queryByTestId("breadcrumb-menu-trigger")).toBeNull()
    },
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
