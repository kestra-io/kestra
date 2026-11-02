import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, within} from "storybook/test"
import {computed, ref} from "vue"
import KsChangeReview, {KS_CHANGE_REVIEW_ALL} from "./KsChangeReview.vue"
import KsTable from "./KsTable/KsTable.vue"
import KsTableColumn from "./KsTable/KsTableColumn.vue"

const meta: Meta<typeof KsChangeReview> = {
    title: "Components/Data/KsChangeReview",
    component: KsChangeReview,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsChangeReview lays out a change to review before it is applied: a heading, a bar of the share of each kind of change, one chip per kind that filters the change (bound with `v-model:filter`), and tabs whose content each tool provides in a slot named after the tab. A segment without a `name` only shows in the bar, its label being the whole text of its legend. It knows nothing of the tool, so a Terraform plan and an Ansible check run share the same layout."}},
    },
}
export default meta
type Story = StoryObj<typeof KsChangeReview>

const resources = [
    {action: "REPLACE", glyph: "±", address: "module.app.terraform_data.app[0]"},
    {action: "CREATE", glyph: "+", address: "module.app.terraform_data.app[1]"},
    {action: "UPDATE", glyph: "~", address: "module.app.terraform_data.app_security_group"},
    {action: "DELETE", glyph: "−", address: "module.app.terraform_data.bastion[0]"},
    {action: "DELETE", glyph: "−", address: "module.app.terraform_data.bastion_security_group[0]"},
]

export const TerraformPlan: Story = {
    render: () => ({
        components: {KsChangeReview, KsTable, KsTableColumn},
        setup() {
            const filter = ref(KS_CHANGE_REVIEW_ALL)
            const shown = computed(() => resources.filter(resource => filter.value === KS_CHANGE_REVIEW_ALL || resource.action === filter.value))
            return {
                review: {
                    heading: "5 changes",
                    meta: ["23 resources in the plan", "Terraform 1.5.7"],
                    segments: [
                        {name: "CREATE", glyph: "+", value: 1, label: "Create", tone: "success"},
                        {name: "REPLACE", glyph: "±", value: 1, label: "Replace", tone: "info"},
                        {name: "UPDATE", glyph: "~", value: 1, label: "Update", tone: "warning"},
                        {name: "DELETE", glyph: "−", value: 2, label: "Destroy", tone: "danger"},
                        {value: 18, label: "18 resources unchanged", tone: "neutral"},
                    ],
                    allLabel: "All changes",
                    allCount: resources.length,
                    filterLabel: "Filter the changes by action",
                    tabs: [
                        {name: "resources", label: "Resources", count: resources.length},
                        {name: "outputs", label: "Outputs", count: 6},
                        {name: "variables", label: "Variables", count: 11},
                    ],
                },
                tab: ref("resources"),
                filter,
                shown,
            }
        },
        template: `
            <div style="padding:24px;max-width:960px">
                <ks-change-review v-model:tab="tab" v-model:filter="filter" v-bind="review">
                    <template #resources>
                        <ks-table :data="shown" rowKey="address" size="small">
                            <ks-table-column prop="glyph" width="56" />
                            <ks-table-column prop="address" label="Address" />
                        </ks-table>
                    </template>
                    <template #outputs><p>The outputs of the plan.</p></template>
                    <template #variables><p>The variables of the plan.</p></template>
                </ks-change-review>
            </div>`,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("5 changes")).toBeInTheDocument()
        await userEvent.click(canvas.getByText("Destroy"))
        await expect(canvas.queryByText("module.app.terraform_data.app[0]")).not.toBeInTheDocument()
        await expect(canvas.getByText("module.app.terraform_data.bastion[0]")).toBeInTheDocument()
        await userEvent.click(canvas.getByText("All changes"))
    },
}

export const AnsibleCheckRun: Story = {
    render: () => ({
        components: {KsChangeReview, KsTable, KsTableColumn},
        setup() {
            return {
                review: {
                    heading: "5 tasks would change",
                    meta: ["ansible-core 2.17", "site.yml", "3 hosts"],
                    segments: [
                        {name: "changed", glyph: "~", value: 5, label: "Would change", tone: "warning"},
                        {value: 21, label: "21 already in place", tone: "success"},
                        {value: 2, label: "2 skipped", tone: "neutral"},
                    ],
                    allLabel: "All tasks",
                    allCount: 5,
                    filterLabel: "Filter the tasks",
                    tabs: [
                        {name: "tasks", label: "Tasks", count: 5},
                        {name: "hosts", label: "Hosts", count: 3},
                    ],
                },
                tab: ref("tasks"),
                tasks: [
                    {host: "web-1", task: "Install nginx", change: "nginx 1.24 → 1.26"},
                    {host: "web-1", task: "Template nginx.conf", change: "worker_connections 512 → 1024"},
                    {host: "web-2", task: "Install nginx", change: "nginx 1.24 → 1.26"},
                ],
            }
        },
        template: `
            <div style="padding:24px;max-width:960px">
                <ks-change-review v-model:tab="tab" v-bind="review">
                    <template #tasks>
                        <ks-table :data="tasks" size="small">
                            <ks-table-column prop="host" label="Host" width="120" />
                            <ks-table-column prop="task" label="Task" />
                            <ks-table-column prop="change" label="Change" />
                        </ks-table>
                    </template>
                    <template #hosts><p>The hosts of the play.</p></template>
                </ks-change-review>
            </div>`,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("5 tasks would change")).toBeInTheDocument()
        await expect(canvas.getByText("21 already in place")).toBeInTheDocument()
        await expect(canvas.getByText("Template nginx.conf")).toBeInTheDocument()
    },
}
