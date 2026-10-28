import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import FlowIcon from "vue-material-design-icons/Sitemap.vue"
import PlusIcon from "vue-material-design-icons/Plus.vue"
import {KsButton, KsInput, KsTopNavBar} from "../../.."

const meta: Meta<typeof KsTopNavBar> = {
    title: "Components/Navigation/KsTopNavBar",
    component: KsTopNavBar,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsTopNavBar is the Kestra design-system top navigation bar. " +
                    "It renders a 60px sticky page header containing the active section's breadcrumb, " +
                    "title with optional leading icon, info tooltip, beta tag and bookmark star, " +
                    "an optional tab select, plus right-side search, action, and dock-toggle slots.",
            },
        },
    },
    argTypes: {
        title: {control: "text"},
        description: {control: "text"},
        beta: {control: "boolean"},
        isBookmarked: {control: "boolean"},
        sidebarCollapsed: {control: "boolean"},
        showDescription: {control: "boolean"},
        showDockToggle: {control: "boolean"},
        isDockOpen: {control: "boolean"},
        activeTab: {control: "text"},
    },
}
export default meta
type Story = StoryObj<typeof KsTopNavBar>

export const Default: Story = {
    render: (args) => ({
        components: {KsTopNavBar},
        setup() { return {args} },
        template: "<ks-top-nav-bar v-bind=\"args\" />",
    }),
    args: {
        title: "Flows",
        description: "",
        beta: false,
        isBookmarked: false,
        hideBookmark: false,
        sidebarCollapsed: false,
        activeTab: "",
        showDescription: false,
        showDockToggle: false,
        isDockOpen: false,
    },
}

export const WithBreadcrumb: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        setup() {
            return {mainIcon: FlowIcon}
        },
        template: `
            <ks-top-nav-bar
                title="my-flow"
                :main-icon="mainIcon"
                :breadcrumb="[
                    {label: 'Flows', link: '#'},
                    {label: 'my-namespace', disabled: true},
                ]"
            />
        `,
    }),
}

export const WithDescriptionAndBeta: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar
                title="Apps"
                description="Standalone UIs powered by a flow."
                beta
            />
        `,
    }),
}

export const Bookmarked: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        template: "<ks-top-nav-bar title=\"Flows\" :is-bookmarked=\"true\" />",
    }),
}

export const WithTabs: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        setup() {
            const tabs = [
                {name: "overview", title: "Overview"},
                {name: "executions", title: "Executions"},
                {name: "topology", title: "Topology"},
                {name: "logs", title: "Logs", disabled: true},
            ]
            const activeTab = ref("overview")
            return {tabs, activeTab}
        },
        template: `
            <ks-top-nav-bar
                title="my-flow"
                :tabs="tabs"
                :active-tab="activeTab"
                @tab-change="(v) => activeTab = v"
            />
        `,
    }),
}

export const WithSidebarToggle: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar
                title="Flows"
                :sidebar-collapsed="true"
                @sidebar-toggle="() => {}"
            />
        `,
    }),
}

export const WithSearchAndActions: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar, KsButton, KsInput},
        setup() {
            return {plus: PlusIcon}
        },
        template: `
            <ks-top-nav-bar
                title="Flows"
                :show-dock-toggle="true"
            >
                <template #search>
                    <ks-input placeholder="Search…" />
                </template>
                <template #actions>
                    <ks-button type="primary" :icon="plus">Create</ks-button>
                </template>
            </ks-top-nav-bar>
        `,
    }),
}

export const CustomTitleSlot: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar title="My Flow">
                <template #title>
                    <span style="color: grey">Deleted:</span>&nbsp;My Flow
                </template>
            </ks-top-nav-bar>
        `,
    }),
}

export const WithDescriptionArea: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar
                title="Executions"
                :show-description="true"
            >
                <template #description>
                    Monitor execution history, inspect logs, and retry failed runs.
                </template>
            </ks-top-nav-bar>
        `,
    }),
}

/** Mobile – the breadcrumb keeps the entity name and the bookmark star is hidden; the app offers the bookmark in the actions menu */
export const Mobile: Story = {
    parameters: {
        controls: {disable: true},
        viewport: {
            options: {
                mobile: {name: "Mobile", styles: {width: "390px", height: "844px"}},
            },
        },
    },
    globals: {
        viewport: {value: "mobile", isRotated: false},
    },
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar
                title="monthly_close"
                :breadcrumb="[{label: 'Executions', onClick: () => {}}, {label: 'company.finance', onClick: () => {}}]"
                sidebar-collapsed
                show-dock-toggle
            />
        `,
    }),
}
