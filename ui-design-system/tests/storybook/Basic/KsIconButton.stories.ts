import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect} from "storybook/test"
import {ref} from "vue"
import KsIconButton from "../../../src/components/Basic/KsIconButton/KsIconButton.vue"

// Inline SVG icons to avoid external dependencies
const TrashIcon = {
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"3 6 5 6 21 6\"/><path d=\"M19 6l-1 14H6L5 6\"/><path d=\"M10 11v6\"/><path d=\"M14 11v6\"/><path d=\"M9 6V4h6v2\"/></svg>",
}
const CopyIcon = {
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\" ry=\"2\"/><path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"/></svg>",
}
const EditIcon = {
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7\"/><path d=\"M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z\"/></svg>",
}
const SearchIcon = {
    template: "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"11\" cy=\"11\" r=\"8\"/><line x1=\"21\" y1=\"21\" x2=\"16.65\" y2=\"16.65\"/></svg>",
}

const meta: Meta<typeof KsIconButton> = {
    title: "Components/Basic/KsIconButton",
    component: KsIconButton,
    tags: ["autodocs"],
    argTypes: {
        tooltip: {control: "text"},
        placement: {
            control: "select",
            options: ["top", "top-start", "top-end", "bottom", "bottom-start", "bottom-end", "left", "right"],
        },
        ariaLabel: {control: "text"},
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsIconButton is a compact 24×24 icon-only button with optional tooltip and router-link support. " +
                    "It is the design-system replacement for the legacy `IconButton.vue` component. " +
                    "Pass an icon component (e.g. from vue-material-design-icons) as the default slot.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsIconButton>

/** Default – no tooltip, click to interact */
export const Default: Story = {
    render: (args) => ({
        components: {KsIconButton, TrashIcon},
        setup() {
            return {args}
        },
        template: "<div style=\"padding:24px\"><ks-icon-button v-bind=\"args\"><trash-icon /></ks-icon-button></div>",
    }),
    args: {},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getByRole("button")
        await expect(btn).toBeTruthy()
        await userEvent.click(btn)
    },
}

/** With tooltip – hover to reveal */
export const WithTooltip: Story = {
    render: (args) => ({
        components: {KsIconButton, TrashIcon},
        setup() {
            return {args}
        },
        template: "<div style=\"padding:48px\"><ks-icon-button v-bind=\"args\"><trash-icon /></ks-icon-button></div>",
    }),
    args: {tooltip: "Delete item", placement: "left"},
}

/** Tooltip placement options */
export const TooltipPlacements: Story = {
    render: () => ({
        components: {KsIconButton, CopyIcon, EditIcon, TrashIcon, SearchIcon},
        template: `
            <div style="padding:48px;display:flex;gap:24px;align-items:center">
                <ks-icon-button tooltip="Copy (top)" placement="top"><copy-icon /></ks-icon-button>
                <ks-icon-button tooltip="Edit (right)" placement="right"><edit-icon /></ks-icon-button>
                <ks-icon-button tooltip="Delete (bottom)" placement="bottom"><trash-icon /></ks-icon-button>
                <ks-icon-button tooltip="Search (left)" placement="left"><search-icon /></ks-icon-button>
            </div>
        `,
    }),
}

/** Disabled state */
export const Disabled: Story = {
    render: () => ({
        components: {KsIconButton, TrashIcon, EditIcon},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-icon-button tooltip="Delete" :disabled="true"><trash-icon /></ks-icon-button>
                <ks-icon-button tooltip="Edit" :disabled="true"><edit-icon /></ks-icon-button>
                <ks-icon-button tooltip="Delete (no tooltip)" :disabled="true"><trash-icon /></ks-icon-button>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const buttons = canvasElement.querySelectorAll(".ks-icon-button")
        for (const btn of buttons) {
            await expect(btn).toHaveAttribute("disabled")
        }
    },
}

/** Action group – multiple icon buttons side by side as seen in table rows */
export const ActionGroup: Story = {
    render: () => ({
        components: {KsIconButton, CopyIcon, EditIcon, TrashIcon},
        setup() {
            const lastAction = ref("")
            return {lastAction}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <div style="display:flex;gap:8px;align-items:center">
                    <ks-icon-button tooltip="Copy" placement="top" @click="lastAction = 'copy'"><copy-icon /></ks-icon-button>
                    <ks-icon-button tooltip="Edit" placement="top" @click="lastAction = 'edit'"><edit-icon /></ks-icon-button>
                    <ks-icon-button tooltip="Delete" placement="top" @click="lastAction = 'delete'"><trash-icon /></ks-icon-button>
                </div>
                <span style="font-size:13px;opacity:0.6">Last action: {{ lastAction || 'none' }}</span>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const buttons = canvas.getAllByRole("button")
        await expect(buttons).toHaveLength(3)
        await userEvent.click(buttons[0])
        await expect(canvas.getByText(/Last action: copy/)).toBeTruthy()
    },
}

/** Custom aria-label independent of tooltip */
export const AriaLabel: Story = {
    render: () => ({
        components: {KsIconButton, TrashIcon},
        template: `
            <div style="padding:24px">
                <ks-icon-button
                    tooltip="Delete"
                    ariaLabel="Delete selected flow"
                >
                    <trash-icon />
                </ks-icon-button>
            </div>
        `,
    }),
    parameters: {
        docs: {
            description: {
                story: "When `ariaLabel` is set it takes precedence over `tooltip` for screen readers.",
            },
        },
    },
}
