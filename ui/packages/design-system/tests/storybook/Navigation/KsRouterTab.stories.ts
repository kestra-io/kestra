import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref, defineComponent} from "vue"
import type {RouterTab} from "../../../src"
import {KsTag, KsRouterTab} from "../../../src"


// Simple content components for stories
const OverviewPanel = defineComponent({
    template: "<div style=\"padding:16px;background:var(--ks-bg-surface);border-radius:4px\">Overview content</div>",
})
const LogsPanel = defineComponent({
    template: "<div style=\"padding:16px;background:var(--ks-bg-surface);border-radius:4px\">Logs content</div>",
})
const MetricsPanel = defineComponent({
    template: "<div style=\"padding:16px;background:var(--ks-bg-surface);border-radius:4px\">Metrics content</div>",
})

const baseTabs: RouterTab[] = [
    {name: "overview", title: "Overview"},
    {name: "logs", title: "Logs"},
    {name: "metrics", title: "Metrics"},
]

const meta: Meta<typeof KsRouterTab> = {
    title: "Components/Navigation/KsRouterTab",
    component: KsRouterTab,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsRouterTab provides a tab navigation bar backed by `vue-router` params " +
                    "(the active tab is driven by `route.params.tab`). " +
                    "It also supports an **embedded mode** via `embedActiveTab` where the parent " +
                    "controls the active tab and listens to the `changed` event — no router required in that mode. " +
                    "App-specific content such as enterprise badges or detail overlays can be injected " +
                    "via the `tab-label` and `content` scoped slots.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsRouterTab>

/** Embedded mode — parent manages the active tab state. No router required. */
export const Default: Story = {
    render: () => ({
        components: {KsRouterTab},
        setup() {
            const activeTab = ref(baseTabs[0].name!)
            const tabs = baseTabs
            return {activeTab, tabs}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name ?? 'overview'"
                />
                <div style="padding:16px;margin-top:16px;background:var(--ks-bg-surface);border-radius:4px">
                    Active: {{ activeTab }}
                </div>
            </div>
        `,
    }),
}

/** Tabs with count badges — pass `count` on the tab definition. */
export const WithCount: Story = {
    render: () => ({
        components: {KsRouterTab},
        setup() {
            const activeTab = ref("executions")
            const tabs: RouterTab[] = [
                {name: "flows", title: "Flows", count: 12},
                {name: "executions", title: "Executions", count: 0},
                {name: "triggers", title: "Triggers", count: 3},
            ]
            return {activeTab, tabs}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name"
                />
            </div>
        `,
    }),
}

/** Disabled tab — disabled tabs show as plain text and cannot be clicked. */
export const WithDisabledTab: Story = {
    render: () => ({
        components: {KsRouterTab},
        setup() {
            const activeTab = ref("overview")
            const tabs: RouterTab[] = [
                {name: "overview", title: "Overview"},
                {name: "topology", title: "Topology", disabled: true},
                {name: "metrics", title: "Metrics"},
            ]
            return {activeTab, tabs}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name"
                />
            </div>
        `,
    }),
}

/** Hidden tab — hidden tabs are not rendered. */
export const WithHiddenTab: Story = {
    render: () => ({
        components: {KsRouterTab},
        setup() {
            const activeTab = ref("overview")
            const tabs: RouterTab[] = [
                {name: "overview", title: "Overview"},
                {name: "internal", title: "Internal", hidden: true},
                {name: "metrics", title: "Metrics"},
            ]
            return {activeTab, tabs}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name"
                />
                <p style="font-size:12px;opacity:0.6;margin-top:8px">The "Internal" tab is hidden and does not appear.</p>
            </div>
        `,
    }),
}

/** Custom tab-label slot — inject badges, icons, or any markup into each label. */
export const CustomLabel: Story = {
    render: () => ({
        components: {KsRouterTab, KsTag},
        setup() {
            const activeTab = ref("overview")
            const tabs: RouterTab[] = [
                {name: "overview", title: "Overview"},
                {name: "beta", title: "Beta Feature"},
                {name: "metrics", title: "Metrics"},
            ]
            return {activeTab, tabs}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name"
                >
                    <template #tab-label="{tab}">
                        <span style="display:inline-flex;align-items:center;gap:6px">
                            {{ tab.title }}
                            <ks-tag v-if="tab.name === 'beta'" type="warning" size="small">Beta</ks-tag>
                        </span>
                    </template>
                </ks-router-tab>
            </div>
        `,
    }),
}

/** Content slot — render the active tab body via the scoped `content` slot. */
export const WithContentSlot: Story = {
    render: () => ({
        components: {KsRouterTab},
        setup() {
            const activeTab = ref("overview")
            const tabs: RouterTab[] = [
                {name: "overview", title: "Overview"},
                {name: "logs", title: "Logs"},
                {name: "metrics", title: "Metrics"},
            ]
            const contentMap: Record<string, string> = {
                overview: "This is the overview panel.",
                logs: "Execution logs appear here.",
                metrics: "Performance metrics are displayed here.",
            }
            return {activeTab, tabs, contentMap}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name"
                    class="container mt-4"
                >
                    <template #content="{activeTab: tab}">
                        <div style="padding:16px;background:var(--ks-bg-surface);border-radius:4px">
                            {{ contentMap[tab.name ?? ''] }}
                        </div>
                    </template>
                </ks-router-tab>
            </div>
        `,
    }),
}

/** Component prop — tabs can declare a `component` to render as content without a slot. */
export const WithComponentProp: Story = {
    render: () => ({
        components: {KsRouterTab},
        setup() {
            const activeTab = ref("overview")
            const tabs: RouterTab[] = [
                {name: "overview", title: "Overview", component: OverviewPanel},
                {name: "logs", title: "Logs", component: LogsPanel},
                {name: "metrics", title: "Metrics", component: MetricsPanel},
            ]
            return {activeTab, tabs}
        },
        template: `
            <div style="padding:24px">
                <ks-router-tab
                    :tabs="tabs"
                    :embed-active-tab="activeTab"
                    @changed="tab => activeTab = tab.name"
                    class="container mt-4"
                />
            </div>
        `,
    }),
}
