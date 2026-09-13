import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsDrillRow from "../../../src/components/Navigation/KsDrillRow/KsDrillRow.vue"

const meta: Meta<typeof KsDrillRow> = {
    title: "Components/Navigation/KsDrillRow",
    component: KsDrillRow,
    tags: ["autodocs"],
    argTypes: {
        label: {control: "text"},
        type: {control: "text"},
        preview: {control: "text"},
        disabled: {control: "boolean"},
        ariaLabel: {control: "text"},
    },
    parameters: {
        docs: {description: {component: "KsDrillRow is a clickable row that opens a nested view: a label, an optional type chip, a one-line preview and a trailing chevron. It is a real `<button>`, so it is keyboard-operable (Enter / Space) and shows a focus ring out of the box."}},
    },
}
export default meta
type Story = StoryObj<typeof KsDrillRow>

export const Default: Story = {
    render: (args) => ({
        components: {KsDrillRow},
        setup() { return {args} },
        template: "<div style=\"padding:24px;max-width:440px\"><KsDrillRow v-bind=\"args\" @open=\"() => {}\" /></div>",
    }),
    args: {label: "country", type: "array<object>", preview: "SELECT · 3 values · required"},
}

export const NoPreview: Story = {
    render: (args) => ({
        components: {KsDrillRow},
        setup() { return {args} },
        template: "<div style=\"padding:24px;max-width:440px\"><KsDrillRow v-bind=\"args\" /></div>",
    }),
    args: {label: "retry", type: "anyOf"},
}

export const LongPreviewTruncates: Story = {
    render: (args) => ({
        components: {KsDrillRow},
        setup() { return {args} },
        template: "<div style=\"padding:24px;max-width:440px\"><KsDrillRow v-bind=\"args\" /></div>",
    }),
    args: {label: "condition 2", type: "Expression", preview: "{{ trigger.date | date('HH') == '09' and execution.state == 'SUCCESS' }}"},
}

export const Disabled: Story = {
    render: (args) => ({
        components: {KsDrillRow},
        setup() { return {args} },
        template: "<div style=\"padding:24px;max-width:440px\"><KsDrillRow v-bind=\"args\" /></div>",
    }),
    args: {label: "outputs", type: "array<object>", preview: "read-only", disabled: true},
}

export const List: Story = {
    render: () => ({
        components: {KsDrillRow},
        template: `
            <div style="padding:24px;max-width:440px;display:flex;flex-direction:column;gap:8px">
                <KsDrillRow label="condition 1" type="ExecutionStatus" preview="in: SUCCESS, WARNING" />
                <KsDrillRow label="condition 2" type="Expression" preview="{{ trigger.date }}" />
                <KsDrillRow label="labels" type="map" preview="env=prod, team=data" />
                <KsDrillRow label="inputs" type="array<object>" preview="country, region · 2 items" />
            </div>
        `,
    }),
}

export const CustomPreviewSlot: Story = {
    render: () => ({
        components: {KsDrillRow},
        template: `
            <div style="padding:24px;max-width:440px">
                <KsDrillRow label="conditions" type="array">
                    <span style="color:var(--ks-text-secondary)">2 conditions set</span>
                </KsDrillRow>
            </div>
        `,
    }),
}
