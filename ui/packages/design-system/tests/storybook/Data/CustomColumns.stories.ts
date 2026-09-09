import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor} from "storybook/test"
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
