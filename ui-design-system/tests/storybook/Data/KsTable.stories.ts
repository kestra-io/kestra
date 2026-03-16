import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTable from "../../../src/components/Data/KsTable/KsTable.vue"
import KsTableColumn from "../../../src/components/Data/KsTable/KsTableColumn.vue"
import KsTag from "../../../src/components/Data/KsTag/KsTag.vue"

const SAMPLE_DATA = [
    {id: "flow-001", namespace: "company.team", status: "SUCCESS", duration: "1.2s"},
    {id: "flow-002", namespace: "company.data", status: "RUNNING", duration: "3.5s"},
    {id: "flow-003", namespace: "company.team", status: "FAILED", duration: "0.8s"},
    {id: "flow-004", namespace: "company.infra", status: "SUCCESS", duration: "2.1s"},
]

const meta: Meta<typeof KsTable> = {
    title: "Components/Data/KsTable",
    component: KsTable,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["large", "default", "small"]},
    },
    parameters: {
        docs: {description: {component: "KsTable is the Kestra design-system abstraction over `ElTable` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTable>

export const Default: Story = {
    args: {
        showHeader: true
    },

    render: (args) => ({
        components: {KsTable, KsTableColumn, KsTag},
        setup() { return {args, SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA" v-bind="args">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                    <ks-table-column prop="status" label="Status">
                        <template #default="{row}">
                            <ks-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'primary' : 'danger'" size="small">
                                {{ row.status }}
                            </ks-tag>
                        </template>
                    </ks-table-column>
                    <ks-table-column prop="duration" label="Duration" />
                </ks-table>
            </div>
        `,
    })
}

export const Sortable: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        setup() { return {SAMPLE_DATA} },
        template: `
            <div style="padding:24px">
                <ks-table :data="SAMPLE_DATA">
                    <ks-table-column prop="id" label="Flow ID" sortable />
                    <ks-table-column prop="namespace" label="Namespace" sortable />
                    <ks-table-column prop="duration" label="Duration" sortable />
                </ks-table>
            </div>
        `,
    }),
}

export const Empty: Story = {
    render: () => ({
        components: {KsTable, KsTableColumn},
        template: `
            <div style="padding:24px">
                <ks-table :data="[]" empty-text="No flows found">
                    <ks-table-column prop="id" label="Flow ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                </ks-table>
            </div>
        `,
    }),
}
