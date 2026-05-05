import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTaskIcon from "../../../src/components/Kestra/KsTaskIcon.vue"

// A simple SVG encoded as base64 to simulate a plugin icon
const mockSvg = "<svg width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"currentColor\" xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M15 9H5V5H15M12 19C11.2044 19 10.4413 18.6839 9.87868 18.1213C9.31607 17.5587 9 16.7956 9 16C9 15.2044 9.31607 14.4413 9.87868 13.8787C10.4413 13.3161 11.2044 13 12 13C12.7956 13 13.5587 13.3161 14.1213 13.8787C14.6839 14.4413 15 15.2044 15 16C15 16.7956 14.6839 17.5587 14.1213 18.1213C13.5587 18.6839 12.7956 19 12 19ZM17 3H5C3.89 3 3 3.9 3 5V19C3 19.5304 3.21071 20.0391 3.58579 20.4142C3.96086 20.7893 4.46957 21 5 21H19C19.5304 21 20.0391 20.7893 20.4142 20.4142C20.7893 20.0391 21 19.5304 21 19V7L17 3Z\" fill=\"currentColor\"/></svg>"
const mockIconBase64 = btoa(mockSvg)

const mockIcons: Record<string, {icon: string; flowable: boolean}> = {
    "io.kestra.plugin.core.log.Log": {
        icon: mockIconBase64,
        flowable: false,
    },
    "io.kestra.plugin.core.flow.Parallel": {
        icon: mockIconBase64,
        flowable: true,
    },
}

const meta: Meta<typeof KsTaskIcon> = {
    title: "Components/Kestra/KsTaskIcon",
    component: KsTaskIcon,
    tags: ["autodocs"],
    argTypes: {
        cls: {control: "text"},
        theme: {control: "select", options: ["dark", "light"]},
        onlyIcon: {control: "boolean"},
        variable: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component: "KsTaskIcon displays a task/plugin icon resolved from the icons registry. Falls back to a generic file icon when no matching icon is found.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsTaskIcon>

export const Default: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.log.Log",
        icons: mockIcons,
        onlyIcon: false,
    },
}

export const OnlyIcon: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.log.Log",
        icons: mockIcons,
        onlyIcon: true,
    },
}

export const FlowableTask: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.flow.Parallel",
        icons: mockIcons,
        onlyIcon: true,
    },
}

export const FallbackIcon: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.unknown.Task",
        icons: mockIcons,
        onlyIcon: true,
    },
}

export const CustomIcon: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        customIcon: {icon: mockIconBase64},
        onlyIcon: true,
    },
}

export const AllSizes: Story = {
    render: () => ({
        components: {KsTaskIcon},
        setup() { return {mockIcons} },
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <div style="width:16px;height:16px"><ks-task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
                <div style="width:24px;height:24px"><ks-task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
                <div style="width:32px;height:32px"><ks-task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
                <div style="width:48px;height:48px"><ks-task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
            </div>
        `,
    }),
}
