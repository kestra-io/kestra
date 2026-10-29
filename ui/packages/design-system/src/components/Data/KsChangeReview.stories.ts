import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, within} from "storybook/test"
import {ref} from "vue"
import KsChangeReview from "./KsChangeReview.vue"
import KsTable from "./KsTable/KsTable.vue"
import KsTableColumn from "./KsTable/KsTableColumn.vue"

const meta: Meta<typeof KsChangeReview> = {
    title: "Components/Data/KsChangeReview",
    component: KsChangeReview,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsChangeReview lays out a change to review before it is applied: a heading, one tile per total, a bar of the share of each kind of change, and tabs whose content each tool provides in a slot named after the tab. It knows nothing of the tool, so a Terraform plan and an Ansible check run share the same layout."}},
    },
}
export default meta
type Story = StoryObj<typeof KsChangeReview>

const terraform = {
    heading: "Terraform plan",
    meta: ["Terraform 1.9.8"],
    tiles: [
        {glyph: "+", count: 3, label: "to add", hint: "2 new, 1 replaced", tone: "success"},
        {glyph: "~", count: 3, label: "to change", tone: "warning"},
        {glyph: "−", count: 3, label: "to destroy", hint: "2 removed, 1 replaced", tone: "danger"},
    ],
    segments: [
        {value: 2, label: "create", tone: "success"},
        {value: 1, label: "replace", tone: "info"},
        {value: 3, label: "update", tone: "warning"},
        {value: 2, label: "destroy", tone: "danger"},
        {value: 18, label: "unchanged", tone: "neutral"},
    ],
    total: "26 resources in the plan",
    tabs: [
        {name: "resources", label: "Resources", count: 8},
        {name: "outputs", label: "Outputs", count: 6},
        {name: "variables", label: "Variables", count: 11},
    ],
} as const

const resources = [
    {action: "-/+", address: "module.app.terraform_data.app[0]", reason: "must be replaced"},
    {action: "+", address: "module.app.terraform_data.app[1]", reason: "will be created"},
    {action: "~", address: "module.app.terraform_data.app_security_group", reason: "updated in-place"},
    {action: "-", address: "module.app.terraform_data.bastion[0]", reason: "will be destroyed"},
]

export const TerraformPlan: Story = {
    render: () => ({
        components: {KsChangeReview, KsTable, KsTableColumn},
        setup() {
            return {review: terraform, tab: ref("resources"), resources}
        },
        template: `
            <div style="padding:24px;max-width:960px">
                <ks-change-review v-model:tab="tab" v-bind="review">
                    <template #resources>
                        <ks-table :data="resources" size="small">
                            <ks-table-column prop="action" width="56" />
                            <ks-table-column prop="address" label="Address" />
                            <ks-table-column prop="reason" label="Change" />
                        </ks-table>
                    </template>
                    <template #outputs><p>The outputs of the plan.</p></template>
                    <template #variables><p>The variables of the plan.</p></template>
                </ks-change-review>
            </div>`,
    }),
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("Terraform plan")).toBeInTheDocument()
        await expect(canvas.getByText("module.app.terraform_data.app[0]")).toBeInTheDocument()
    },
}

export const AnsibleCheckRun: Story = {
    render: () => ({
        components: {KsChangeReview, KsTable, KsTableColumn},
        setup() {
            return {
                review: {
                    heading: "Ansible check run",
                    meta: ["ansible-core 2.17", "site.yml", "3 hosts"],
                    tiles: [
                        {glyph: "~", count: 5, label: "would change", hint: "on 2 hosts", tone: "warning"},
                        {glyph: "✓", count: 21, label: "already in place", tone: "success"},
                        {glyph: "!", count: 0, label: "unreachable", tone: "danger"},
                    ],
                    segments: [
                        {value: 5, label: "changed", tone: "warning"},
                        {value: 21, label: "ok", tone: "success"},
                        {value: 2, label: "skipped", tone: "neutral"},
                    ],
                    total: "28 tasks",
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
        await expect(canvas.getByText("Ansible check run")).toBeInTheDocument()
        await expect(canvas.getByText("Template nginx.conf")).toBeInTheDocument()
    },
}
