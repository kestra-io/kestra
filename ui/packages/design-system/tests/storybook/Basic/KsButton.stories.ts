import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect, waitFor} from "storybook/test"
import {markRaw, ref} from "vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

// Inline SVG icons as components to avoid external dependencies
const DownloadIcon = markRaw({
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1em\" height=\"1em\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"/><polyline points=\"7 10 12 15 17 10\"/><line x1=\"12\" y1=\"15\" x2=\"12\" y2=\"3\"/></svg>",
})
const PlusIcon = markRaw({
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1em\" height=\"1em\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"/><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/></svg>",
})

const meta: Meta<typeof KsButton> = {
    title: "Components/Basic/KsButton",
    component: KsButton,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["default", "primary", "success", "warning", "info", "danger"]},
        size: {control: "select", options: ["small", "default", "large"]},
        tooltip: {control: "text"},
        disabled: {control: "boolean"},
        loading: {control: "boolean"},
        plain: {control: "boolean"},
        round: {control: "boolean"},
        circle: {control: "boolean"},
        text: {control: "boolean"},
        link: {control: "boolean"},
        bg: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsButton is the Kestra design-system abstraction over `ElButton` from Element Plus. " +
                    "Only the props, events and slots actually used across the Kestra UI are exposed.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsButton>

/** Default button */
export const Default: Story = {
    render: (args) => ({
        components: {KsButton},
        setup() {
            return {args}
        },
        template: "<div style=\"padding:24px\"><ks-button v-bind=\"args\">Button</ks-button></div>",
    }),
    args: {type: "default"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toBeTruthy()
        await userEvent.click(btn)
    },
}

/** All types side by side */
export const Types: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            return {DownloadIcon}
        },
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:12px;align-items:center">
                <ks-button :icon="DownloadIcon">Default</ks-button>
                <ks-button :icon="DownloadIcon" type="primary">Primary</ks-button>
                <ks-button :icon="DownloadIcon" type="success">Success</ks-button>
                <ks-button :icon="DownloadIcon" type="warning">Warning</ks-button>
                <ks-button :icon="DownloadIcon" type="danger">Danger</ks-button>
                <ks-button :icon="DownloadIcon" type="info">Info</ks-button>
                <ks-button :icon="DownloadIcon" text>Text</ks-button>
            </div>

            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:12px;align-items:center">
                <ks-button disabled :icon="DownloadIcon">Default</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="primary">Primary</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="success">Success</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="warning">Warning</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="danger">Danger</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="info">Info</ks-button>
                <ks-button disabled :icon="DownloadIcon" text>Text</ks-button>
            </div>
        `,
    }),
}

/** All sizes */
export const Sizes: Story = {
    render: () => ({
        components: {KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-button type="primary" size="large">Large</ks-button>
                <ks-button type="primary">Default</ks-button>
                <ks-button type="primary" size="small">Small</ks-button>
            </div>
        `,
    }),
}

/** Plain variant */
export const Plain: Story = {
    render: () => ({
        components: {KsButton},
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:12px;align-items:center">
                <ks-button plain>Default</ks-button>
                <ks-button type="primary" plain>Primary</ks-button>
                <ks-button type="success" plain>Success</ks-button>
                <ks-button type="warning" plain>Warning</ks-button>
                <ks-button type="danger" plain>Danger</ks-button>
                <ks-button type="info" plain>Info</ks-button>
            </div>
        `,
    }),
}

/** Round and circle variants */
export const RoundAndCircle: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            return {PlusIcon}
        },
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-button type="primary" round>Round</ks-button>
                <ks-button type="primary" :icon="PlusIcon" circle />
                <ks-button type="success" :icon="PlusIcon" circle />
            </div>
        `,
    }),
}

/** Disabled state */
export const Disabled: Story = {
    render: () => ({
        components: {KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-button disabled :icon="DownloadIcon">Default</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="primary">Primary</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="success">Success</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="warning">Warning</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="danger">Danger</ks-button>
                <ks-button disabled :icon="DownloadIcon" type="info">Info</ks-button>
                <ks-button disabled :icon="DownloadIcon" text>Text</ks-button>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const buttons = canvas.getAllByRole("button")
        for (const btn of buttons) {
            await expect(btn).toBeDisabled()
        }
    },
}

/** Loading state – spinner shown while an async action is in progress */
export const Loading: Story = {
    render: (args) => ({
        components: {KsButton},
        setup() {
            return {args}
        },
        template: "<div style=\"padding:24px\"><ks-button v-bind=\"args\">Saving…</ks-button></div>",
    }),
    args: {type: "primary", loading: true},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toBeDisabled()
        await expect(canvasElement.querySelector(".kel-button.is-loading")).toBeTruthy()
    },
}

/** With icon – icon rendered to the left of the label */
export const WithIcon: Story = {
    render: (args) => ({
        components: {KsButton},
        setup() {
            return {args, DownloadIcon}
        },
        template: "<div style=\"padding:24px\"><ks-button v-bind=\"args\" :icon=\"DownloadIcon\">Download</ks-button></div>",
    }),
    args: {type: "primary"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toBeTruthy()
        await expect(canvasElement.querySelector(".kel-icon")).toBeTruthy()
    },
}

/** Text and link variants */
export const TextAndLink: Story = {
    render: () => ({
        components: {KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-button text>Text button</ks-button>
                <ks-button text type="primary">Text primary</ks-button>
                <ks-button link type="primary">Link button</ks-button>
                <ks-button text type="primary" bg>With background</ks-button>
            </div>
        `,
    }),
}

/** Custom tag – render as anchor or div instead of button */
export const CustomTag: Story = {
    render: () => ({
        components: {KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-button tag="a" href="#" type="primary">Anchor tag</ks-button>
                <ks-button tag="div" type="success">Div tag</ks-button>
            </div>
        `,
    }),
}

/** Custom color – auto-calculates hover and active states */
export const CustomColor: Story = {
    render: () => ({
        components: {KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-button color="#626aef">Custom purple</ks-button>
                <ks-button color="#626aef" plain>Plain purple</ks-button>
                <ks-button color="#e07b54">Custom orange</ks-button>
            </div>
        `,
    }),
}

/** Tooltip – icon-only buttons get a hover tooltip; aria-label is derived from it */
export const Tooltip: Story = {
    render: (args) => ({
        components: {KsButton},
        setup() {
            return {args, PlusIcon}
        },
        template: "<div style=\"padding:48px\"><ks-button v-bind=\"args\" :icon=\"PlusIcon\" /></div>",
    }),
    args: {type: "default", tooltip: "Add label"},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toHaveAttribute("aria-label", "Add label")
        await userEvent.hover(btn)
        await waitFor(() =>
            expect(document.body.querySelector("[role=\"tooltip\"]")?.textContent).toContain("Add label"),
        )
    },
}

/** Click event emission */
export const ClickEvent: Story = {
    render: () => ({
        components: {KsButton},
        setup() {
            const clicks = ref(0)
            function handleClick() {
                clicks.value++
            }
            return {clicks, handleClick}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-button type="primary" @click="handleClick">Click me</ks-button>
                <span style="font-size:13px;opacity:0.6">Clicks: {{ clicks }}</span>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await userEvent.click(btn)
        await userEvent.click(btn)
        await expect(canvas.getByText(/Clicks: 2/)).toBeTruthy()
    },
}
