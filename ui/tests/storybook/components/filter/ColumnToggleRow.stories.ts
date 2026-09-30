import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect, fireEvent, fn, waitFor, within} from "storybook/test"
import ColumnToggleRow from "../../../../packages/design-system/src/components/Data/KsDataTable/filter/ColumnToggleRow.vue"
import type {ColumnConfig} from "../../../../packages/design-system/src/components/Data/KsDataTable/filter/composables/useTableColumns"

const NAMESPACE: ColumnConfig = {
    label: "Namespace",
    prop: "namespace",
    default: true,
    description: "Namespace the flow belongs to",
}

const meta: Meta<typeof ColumnToggleRow> = {
    title: "Components/Filter/ColumnToggleRow",
    component: ColumnToggleRow,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "One row of the \"Customize columns\" panel behind `KsDataTable`, rendered by `DraggableTableColumns` for every available column. The row is controlled: it displays `checked` and emits `toggle`, so the switch only moves once the parent updates the visible columns. `showHandle` adds the drag affordance used by the reorderable ungrouped list; without it the row is indented as a member of a group. `showDescription` drops the column description where the surrounding panel already gives that context (grouped rows and search results).",
            },
        },
    },
    args: {
        onToggle: fn(),
    },
    render: (args) => ({
        components: {ColumnToggleRow},
        setup() {
            return {args}
        },
        template: "<div style=\"width:320px\"><ColumnToggleRow v-bind=\"args\" /></div>",
    }),
}

export default meta

type Story = StoryObj<typeof ColumnToggleRow>

export const Hidden: Story = {
    args: {column: NAMESPACE, checked: false},
    async play({canvasElement, args}) {
        const canvas = within(canvasElement)
        const toggle = canvas.getByRole("switch", {name: NAMESPACE.label})

        await fireEvent.click(toggle.parentElement!)

        expect(args.onToggle).toHaveBeenCalledTimes(1)
        expect(toggle).toHaveAttribute("aria-checked", "false")
    },
}

export const Visible: Story = {
    args: {column: NAMESPACE, checked: true},
}

export const Draggable: Story = {
    args: {column: NAMESPACE, checked: true, showHandle: true},
}

export const InGroup: Story = {
    args: {
        column: {
            label: "Duration",
            prop: "duration",
            default: false,
            group: "Metrics",
            description: "Total execution duration",
        },
        checked: false,
        showDescription: false,
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        expect(canvas.getByText("Duration")).toBeVisible()
        expect(canvas.queryByText("Total execution duration")).toBeNull()
    },
}

export const ColumnList: Story = {
    render: () => ({
        components: {ColumnToggleRow},
        setup() {
            const columns: ColumnConfig[] = [
                NAMESPACE,
                {label: "Flow", prop: "flowId", default: true, description: "Flow that produced the execution"},
                {label: "Labels", prop: "labels", default: false, description: "Labels attached to the execution"},
                {label: "Duration", prop: "duration", default: false, group: "Metrics", description: "Total execution duration"},
            ]
            const visible = ref(["namespace", "flowId"])
            const toggle = (prop: string) => {
                visible.value = visible.value.includes(prop)
                    ? visible.value.filter(p => p !== prop)
                    : [...visible.value, prop]
            }

            return {columns, visible, toggle}
        },
        template: `
            <div style="width:320px">
                <ColumnToggleRow
                    v-for="column in columns"
                    :key="column.prop"
                    :column="column"
                    :checked="visible.includes(column.prop)"
                    :showHandle="!column.group"
                    :showDescription="!column.group"
                    @toggle="toggle(column.prop)"
                />
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const toggle = canvas.getByRole("switch", {name: NAMESPACE.label})
        expect(toggle).toHaveAttribute("aria-checked", "true")

        await fireEvent.click(toggle.parentElement!)

        await waitFor(() => expect(canvas.getByRole("switch", {name: NAMESPACE.label})).toHaveAttribute("aria-checked", "false"))
    },
}
