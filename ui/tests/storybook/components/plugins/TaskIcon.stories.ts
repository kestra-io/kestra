import type {Meta, StoryObj} from "@storybook/vue3-vite"
import TaskIcon from "../../../../src/components/plugins/TaskIcon.vue"

const ecosystemIconDataUri = `data:image/svg+xml,${encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M12 2l9 5v10l-9 5-9-5V7z\" fill=\"#3776AB\"/></svg>")}`

const mockIcons: Record<string, {flowable: boolean; monochrome: boolean; hasIcon: boolean; iconUrl?: string}> = {
    "io.kestra.plugin.core.log.Log": {
        flowable: false,
        monochrome: false,
        hasIcon: true,
    },
    "io.kestra.plugin.core.flow.Parallel": {
        flowable: true,
        monochrome: false,
        hasIcon: true,
    },
    "io.kestra.plugin.core.debug.Echo": {
        flowable: false,
        monochrome: true,
        hasIcon: true,
    },
    "io.kestra.plugin.core.debug.NoIcon": {
        flowable: false,
        monochrome: false,
        hasIcon: false,
    },
    "io.kestra.plugin.scripts.python.Commands": {
        flowable: false,
        monochrome: false,
        hasIcon: true,
        iconUrl: ecosystemIconDataUri,
    },
}

const meta: Meta<typeof TaskIcon> = {
    title: "Components/Plugins/TaskIcon",
    component: TaskIcon,
    tags: ["autodocs"],
    argTypes: {
        cls: {control: "text"},
        onlyIcon: {control: "boolean"},
        variable: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component: "TaskIcon renders the plugin icon as a real, browser-cacheable `image/svg+xml` resource served from `GET /api/v1/plugins/icons/{cls}/icon.svg` — no more client-side base64 decode/recolor/encode on every render. Most icons ship fixed brand colors and render as a plain `<img>`. The rare single-color icon (flagged `monochrome` in the resolved icon metadata) is instead rendered via a CSS `mask-image` so it can be recolored through the CSS cascade (the `variable` prop, or `--ks-text-primary` by default). It resolves icon metadata synchronously from the `icons` map when provided, or lazily via the `loadIcon` prop so callers don't have to preload the whole plugin-icons catalog. Every registered class gets an `icons` entry regardless of whether it ships an icon (`hasIcon`), so TaskIcon falls back to a generic file icon both when the class is unknown and when it's known but iconless.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof TaskIcon>

export const Default: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.log.Log",
        icons: mockIcons,
        onlyIcon: false,
    },
}

export const OnlyIcon: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.log.Log",
        icons: mockIcons,
        onlyIcon: true,
    },
}

export const FlowableTask: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.flow.Parallel",
        icons: mockIcons,
        onlyIcon: true,
    },
}

export const MonochromeIcon: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
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
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.unknown.Task",
        icons: mockIcons,
        onlyIcon: true,
    },
}

export const RegisteredWithoutIcon: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.core.debug.NoIcon",
        icons: mockIcons,
        onlyIcon: true,
    },
    parameters: {
        docs: {
            description: {
                story: "A class can be registered (present in the `icons` map, with a real `flowable` value) without shipping an icon file at all — `hasIcon: false` — in which case TaskIcon falls back to the generic icon rather than pointing an `<img>` at a URL that would 404.",
            },
        },
    },
}

export const EcosystemCatalogIcon: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        cls: "io.kestra.plugin.scripts.python.Commands",
        icons: mockIcons,
        onlyIcon: true,
    },
    parameters: {
        docs: {
            description: {
                story: "Icons resolved from the external api.kestra.io plugin catalog (for ecosystem plugins not installed on this instance, e.g. shown in Blueprints) carry a pre-resolved `iconUrl` — this instance has no local endpoint that could serve their bytes, so they're embedded as a data URI instead of pointed at `/icon.svg`.",
            },
        },
    },
}

const customSvgDataUri = `data:image/svg+xml,${encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><rect width=\"24\" height=\"24\" rx=\"4\" fill=\"#8405FF\"/></svg>")}`
const customMonochromeSvgDataUri = `data:image/svg+xml,${encodeURIComponent("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"10\" fill=\"currentColor\"/></svg>")}`

export const CustomIcon: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
    }),
    args: {
        customIcon: {icon: customSvgDataUri},
        onlyIcon: true,
    },
}

export const CustomIconMonochrome: Story = {
    render: (args) => ({
        components: {TaskIcon},
        setup() { return {args} },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" /></div>",
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
        components: {TaskIcon},
        setup() {
            // simulates pluginsStore.loadIcon: fetches one icon on demand instead of
            // requiring the whole plugin-icons catalog to be preloaded up front
            const loadIcon = (cls: string) => new Promise<{flowable: boolean; monochrome: boolean; hasIcon: boolean} | undefined>(resolve => {
                setTimeout(() => resolve(mockIcons[cls]), 1000)
            })
            return {args, loadIcon}
        },
        template: "<div style=\"width:32px;height:32px\"><task-icon v-bind=\"args\" :loadIcon=\"loadIcon\" /></div>",
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
        components: {TaskIcon},
        setup() { return {mockIcons} },
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <div style="width:16px;height:16px"><task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
                <div style="width:24px;height:24px"><task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
                <div style="width:32px;height:32px"><task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
                <div style="width:48px;height:48px"><task-icon cls="io.kestra.plugin.core.log.Log" :icons="mockIcons" only-icon /></div>
            </div>
        `,
    }),
}
