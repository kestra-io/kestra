import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsSelectableListItem from "./KsSelectableListItem.vue"
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"

const meta: Meta<typeof KsSelectableListItem> = {
    title: "Components/Navigation/KsSelectableListItem",
    component: KsSelectableListItem,
    tags: ["autodocs"],
}
export default meta
type Story = StoryObj<typeof KsSelectableListItem>

export const States: Story = {
    render: () => ({
        components: {KsSelectableListItem, FileDocumentOutline},
        template: `
            <div style="display:flex;flex-direction:column;gap:var(--ks-spacing-1);max-width:24rem;padding:var(--ks-spacing-4);background:var(--ks-bg-surface)">
                <ks-selectable-list-item title="Selected artifact" subtitle="application/json" selected>
                    <template #leading><FileDocumentOutline /></template>
                </ks-selectable-list-item>
                <ks-selectable-list-item title="A long artifact title that truncates cleanly in a narrow list" subtitle="A long optional subtitle that truncates cleanly too">
                    <template #leading><FileDocumentOutline /></template>
                </ks-selectable-list-item>
                <ks-selectable-list-item title="Disabled artifact" subtitle="Not available" disabled>
                    <template #leading><FileDocumentOutline /></template>
                </ks-selectable-list-item>
            </div>
        `,
    }),
    parameters: {controls: {disable: true}},
}
