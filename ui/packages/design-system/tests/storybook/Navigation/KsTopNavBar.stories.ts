import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {KsButton, KsDropdownItem, KsDropdownMenu, KsTag, KsTopNavBar} from "../../../src"

const meta: Meta<typeof KsTopNavBar> = {
    title: "Components/Navigation/KsTopNavBar",
    component: KsTopNavBar,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsTopNavBar is the Kestra design-system top navigation bar component. " +
                    "It provides a sticky page header with breadcrumb, title, bookmark star, description, " +
                    "and composable slots for search, actions, and additional right-side content.",
            },
        },
    },
    argTypes: {
        title: {control: "text"},
        description: {control: "text"},
        longDescription: {control: "text"},
        beta: {control: "boolean"},
        isBookmarked: {control: "boolean"},
    },
}
export default meta
type Story = StoryObj<typeof KsTopNavBar>

export const Default: Story = {
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar title="Flows" />
        `,
    }),
}

export const WithBreadcrumb: Story = {
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar
                title="my-flow"
                :breadcrumb="[
                    {label: 'Flows', link: '#'},
                    {label: 'my-namespace', disabled: true},
                ]"
            />
        `,
    }),
}

export const WithDescription: Story = {
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar
                title="Executions"
                description="View all executions for this flow"
                long-description="Monitor execution history, inspect logs and retry failed runs."
            />
        `,
    }),
}

export const BetaBadge: Story = {
    render: () => ({
        components: {KsTopNavBar, KsTag},
        template: `
            <ks-top-nav-bar title="Apps">
                <template #badge>
                    <ks-tag type="primary">Beta</ks-tag>
                </template>
            </ks-top-nav-bar>
        `,
    }),
}

export const Bookmarked: Story = {
    render: () => ({
        components: {KsTopNavBar},
        template: `
            <ks-top-nav-bar title="Flows" :is-bookmarked="true" />
        `,
    }),
}

export const WithActions: Story = {
    render: () => ({
        components: {KsTopNavBar, KsButton, KsDropdownMenu, KsDropdownItem},
        template: `
            <ks-top-nav-bar title="Flows">
                <template #more-actions>
                    <ks-dropdown-item command="edit">Edit</ks-dropdown-item>
                    <ks-dropdown-item command="duplicate">Duplicate</ks-dropdown-item>
                    <ks-dropdown-item divided command="delete" disabled>Delete</ks-dropdown-item>
                </template>
                <template #actions>
                    <ks-button type="primary">Create</ks-button>
                </template>
            </ks-top-nav-bar>
        `,
    }),
}

export const CustomTitle: Story = {
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
