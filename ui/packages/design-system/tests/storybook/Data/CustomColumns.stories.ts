import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor, within} from "storybook/test"
import CustomColumns from "../../../src/components/Data/KsDataTable/filter/segments/CustomColumns.vue"

const columns = Array.from({length: 12}, (_, index) => ({
    label: `Column ${index + 1}`,
    prop: `column-${index + 1}`,
    default: index < 8,
}))

const meta: Meta<typeof CustomColumns> = {
    title: "Components/Data/CustomColumns",
    component: CustomColumns,
    args: {
        storageKey: "custom-columns-story",
        columns,
        visibleColumns: columns.filter(column => column.default).map(column => column.prop),
    },
    render: args => ({
        components: {CustomColumns},
        setup: () => ({args}),
        template: `
            <div style="width: 300px">
                <CustomColumns v-bind="args" />
            </div>
        `,
    }),
}

export default meta
type Story = StoryObj<typeof CustomColumns>

export const ScrollableListWithFixedFooter: Story = {
    async play({canvasElement}) {
        const panel = canvasElement.querySelector<HTMLElement>(".customize-columns-panel")!
        const list = panel.querySelector<HTMLElement>(".list")!
        const footer = panel.querySelector<HTMLElement>(".footer")!

        expect(list.scrollHeight).toBeGreaterThan(list.clientHeight)

        const footerTop = footer.getBoundingClientRect().top
        list.scrollTop = list.scrollHeight

        await waitFor(() => expect(list.scrollTop).toBeGreaterThan(0))
        expect(footer.getBoundingClientRect().top).toBe(footerTop)
    },
}

export const Grouped: Story = {
    args: {
        storageKey: "custom-columns-grouped-story",
        columns: [
            {label: "Id", prop: "id", default: true},
            {label: "State", prop: "state", default: true},
            {label: "Owner", prop: "meta:owner", default: true, group: "Metadata"},
            {label: "Team", prop: "meta:team", default: false, group: "Metadata"},
            {label: "Region", prop: "meta:region", default: false, group: "Metadata"},
        ],
        visibleColumns: ["id", "state", "meta:owner"],
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        // A collapsed group is out of the accessibility tree, not merely out of sight.
        expect(canvas.queryByRole("switch", {name: "Team"})).toBeNull()

        await userEvent.click(canvas.getByRole("button", {name: /Metadata/}))

        await waitFor(() => expect(canvas.queryByRole("switch", {name: "Team"})).not.toBeNull())
        expect(canvasElement.querySelectorAll(".column-group .drag-handle")).toHaveLength(0)
    },
}

// Rows go flat and undraggable while searching: reordering a filtered subset would write an order
// the user cannot see.
export const Searchable: Story = {
    args: {
        storageKey: "custom-columns-searchable-story",
        columns: [
            ...Array.from({length: 12}, (_, index) => ({
                label: `Column ${index + 1}`,
                prop: `column-${index + 1}`,
                default: index < 8,
            })),
            {label: "Namespace", prop: "namespace", default: true},
        ],
        visibleColumns: ["namespace"],
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const search = canvasElement.querySelector<HTMLInputElement>(".search input")!

        expect(search).toBeVisible()

        await userEvent.type(search, "names")

        await waitFor(() => expect(canvas.getAllByRole("switch")).toHaveLength(1))
        expect(canvas.queryByRole("switch", {name: "Namespace"})).not.toBeNull()
        expect(canvasElement.querySelector(".drag-handle")).toBeNull()
    },
}
