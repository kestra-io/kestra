import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTaskIcon from "../../../src/components/Kestra/KsTaskIcon.vue"

const mockIcons: Record<string, {flowable: boolean; monochrome: boolean}> = {
    "io.kestra.plugin.core.log.Log": {
        flowable: false,
        monochrome: false,
    },
    "io.kestra.plugin.core.flow.Parallel": {
        flowable: true,
        monochrome: false,
    },
    "io.kestra.plugin.core.debug.Echo": {
        flowable: false,
        monochrome: true,
    },
}

const meta: Meta<typeof KsTaskIcon> = {
    title: "Components/Kestra/KsTaskIcon",
    component: KsTaskIcon,
    tags: ["autodocs"],
    argTypes: {
        cls: {control: "text"},
        onlyIcon: {control: "boolean"},
        variable: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component: "KsTaskIcon renders the plugin icon as a real, browser-cacheable `image/svg+xml` resource served from `GET /api/v1/plugins/icons/{cls}/svg` — no more client-side base64 decode/recolor/encode on every render. Most icons ship fixed brand colors and render as a plain `<img>`. The rare single-color icon (flagged `monochrome` in the resolved icon metadata) is instead rendered via a CSS `mask-image` so it can be recolored through the CSS cascade (the `variable` prop, or `--ks-text-primary` by default). It resolves icon metadata synchronously from the `icons` map when provided, or lazily via the `loadIcon` prop so callers don't have to preload the whole plugin-icons catalog. Falls back to a generic file icon when no matching icon is found.",
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

export const MonochromeIcon: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.debug.Echo",
        icons: mockIcons,
        onlyIcon: true,
    },
    parameters: {
        docs: {
            description: {
                story: "Icons whose source SVG uses `currentColor` are flagged `monochrome` and rendered via a CSS `mask-image` so they recolor with the surrounding theme instead of shipping a fixed color.",
            },
        },
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

// A literal SVG override, provided directly as a data URI rather than resolved from `cls`
const customSvgDataUri = `data:image/svg+xml,${encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><rect width=\"24\" height=\"24\" rx=\"4\" fill=\"#8405FF\"/></svg>")}`
const customMonochromeSvgDataUri = `data:image/svg+xml,${encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"10\" fill=\"currentColor\"/></svg>")}`

export const CustomIcon: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        customIcon: {icon: customSvgDataUri},
        onlyIcon: true,
    },
}

export const CustomIconMonochrome: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        customIcon: {icon: customMonochromeSvgDataUri, monochrome: true},
        onlyIcon: true,
        variable: "--ks-text-error",
    },
    parameters: {
        docs: {
            description: {
                story: "A literal SVG override flagged `monochrome` renders through the same CSS mask path as a resolved plugin icon, so it also recolors via the `variable` prop.",
            },
        },
    },
}

export const LazyLoaded: Story = {
    render: (args) => ({
        components: {KsTaskIcon},
        setup() {
            // simulates pluginsStore.loadIcon: fetches one icon on demand instead of
            // requiring the whole plugin-icons catalog to be preloaded up front
            const loadIcon = (cls: string) => new Promise<{flowable: boolean; monochrome: boolean} | undefined>(resolve => {
                setTimeout(() => resolve(mockIcons[cls]), 1000)
            })
            return {args, loadIcon}
        },
        template: "<div style=\"width:32px;height:32px\"><ks-task-icon v-bind=\"args\" :loadIcon=\"loadIcon\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.log.Log",
        onlyIcon: true,
    },
    parameters: {
        docs: {
            description: {
                story: "No `icons` map is passed here — the icon is resolved on demand via `loadIcon` (shown after a simulated 1s network delay), falling back to the generic icon until it resolves.",
            },
        },
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
