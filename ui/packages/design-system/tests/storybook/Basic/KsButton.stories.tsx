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
        template: "<div style=\"padding:24px\"><KsButton v-bind=\"args\">Button</KsButton></div>",
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
        setup() {
            return () => (<>
                <div style="padding:24px;display:flex;flex-wrap:wrap;gap:12px;align-items:center">
                    <KsButton icon={DownloadIcon}>Default</KsButton>
                    <KsButton icon={DownloadIcon} type="primary">Primary</KsButton>
                    <KsButton icon={DownloadIcon} type="success">Success</KsButton>
                    <KsButton icon={DownloadIcon} type="warning">Warning</KsButton>
                    <KsButton icon={DownloadIcon} type="danger">Danger</KsButton>
                    <KsButton icon={DownloadIcon} type="info">Info</KsButton>
                    <KsButton icon={DownloadIcon} text>Text</KsButton>
                </div>

                <div style="padding:24px;display:flex;flex-wrap:wrap;gap:12px;align-items:center">
                    <KsButton disabled icon={DownloadIcon}>Default</KsButton>
                    <KsButton disabled icon={DownloadIcon} type="primary">Primary</KsButton>
                    <KsButton disabled icon={DownloadIcon} type="success">Success</KsButton>
                    <KsButton disabled icon={DownloadIcon} type="warning">Warning</KsButton>
                    <KsButton disabled icon={DownloadIcon} type="danger">Danger</KsButton>
                    <KsButton disabled icon={DownloadIcon} type="info">Info</KsButton>
                    <KsButton disabled icon={DownloadIcon} text>Text</KsButton>
                </div>
            </>)
        }
            
    }),
}

/** All sizes */
export const Sizes: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <KsButton type="primary" size="large">Large</KsButton>
                    <KsButton type="primary">Default</KsButton>
                    <KsButton type="primary" size="small">Small</KsButton>
                </div>
            )
        }
            
    }),
}

/** Round and circle variants */
export const RoundAndCircle: Story = {
    render: () => ({
        setup() {
            return () => (<div style="padding:24px;display:flex;gap:12px;align-items:center">
                <KsButton type="primary" round>Round</KsButton>
                <KsButton type="primary" icon={PlusIcon} circle />
                <KsButton type="success" icon={PlusIcon} circle />
                </div>
            ) 
        }   
    }),
}

/** Disabled state */
export const Disabled: Story = {
    render: () => ({
        setup() {
         
            return () => (<div style="padding:24px;display:flex;gap:12px;align-items:center">
                <KsButton disabled icon={DownloadIcon}>Default</KsButton>
                <KsButton disabled icon={DownloadIcon} type="primary">Primary</KsButton>
                <KsButton disabled icon={DownloadIcon} type="success">Success</KsButton>
                <KsButton disabled icon={DownloadIcon} type="warning">Warning</KsButton>
                <KsButton disabled icon={DownloadIcon} type="danger">Danger</KsButton>
                <KsButton disabled icon={DownloadIcon} type="info">Info</KsButton>
                <KsButton disabled icon={DownloadIcon} text>Text</KsButton>
            </div>)
        },
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
        template: "<div style=\"padding:24px\"><KsButton v-bind=\"args\">Saving…</KsButton></div>",
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
        setup() {
            return () => <div style="padding:24px"><KsButton {...args} icon={DownloadIcon}>Download</KsButton></div>
        }
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
        setup() {
            return () => (
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <KsButton text>Text button</KsButton>
                    <KsButton text type="primary">Text primary</KsButton>
                    <KsButton link type="primary">Link button</KsButton>
                    <KsButton text type="primary" bg>With background</KsButton>
                </div>
            )
        }
    }),
}

/** Custom tag – render as anchor or div instead of button */
export const CustomTag: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <KsButton tag="a" href="#" type="primary">Anchor tag</KsButton>
                    <KsButton tag="div" type="success">Div tag</KsButton>
                </div>
            )
        }
    }),
}

/** Custom color – auto-calculates hover and active states */
export const CustomColor: Story = {
    render: () => ({
        setup() {
            return () => (
                <div style="padding:24px;display:flex;gap:12px;align-items:center">
                    <KsButton color="#626aef">Custom purple</KsButton>
                    <KsButton color="#e07b54">Custom orange</KsButton>
                </div>
            )
        }
    }),
}

/** Tooltip – icon-only buttons get a hover tooltip; aria-label is derived from it */
export const Tooltip: Story = {
    render: (args) => ({
        setup() {
            return () => <div style="padding:48px"><KsButton {...args} icon={PlusIcon} /></div>
        }
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
        setup() {
            const clicks = ref(0)
            function handleClick() {
                clicks.value++
            }
            return () => (
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <KsButton type="primary" onClick={handleClick}>Click me</KsButton>
                <span style="font-size:13px;opacity:0.6">Clicks: {clicks.value}</span>
            </div>
            )
        }
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await userEvent.click(btn)
        await userEvent.click(btn)
        await expect(canvas.getByText(/Clicks: 2/)).toBeTruthy()
    },
}
