import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTree from "../../../src/components/Data/KsTree.vue"

const TREE_DATA = [
    {
        label: "company",
        children: [
            {
                label: "team",
                children: [
                    {label: "flow-a"},
                    {label: "flow-b"},
                ],
            },
            {
                label: "data",
                children: [
                    {label: "pipeline-1"},
                ],
            },
        ],
    },
]

const meta: Meta<typeof KsTree> = {
    title: "Components/Data/KsTree",
    component: KsTree,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsTree is the Kestra design-system abstraction over `ElTree` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTree>

export const Default: Story = {
    render: (args) => ({
        components: {KsTree},
        setup() { return {args, TREE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-tree
                    :data="TREE_DATA"
                    :props="{label: 'label', children: 'children'}"
                    default-expand-all
                    v-bind="args"
                />
            </div>
        `,
    }),
}

export const WithCustomSlot: Story = {
    render: () => ({
        components: {KsTree},
        setup() { return {TREE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-tree
                    :data="TREE_DATA"
                    :props="{label: 'label', children: 'children'}"
                    default-expand-all
                >
                    <template #default="{node, data}">
                        <span>{{ node.label }}</span>
                        <span v-if="!data.children" style="margin-left:8px;opacity:0.5;font-size:12px">(leaf)</span>
                    </template>
                </ks-tree>
            </div>
        `,
    }),
}
