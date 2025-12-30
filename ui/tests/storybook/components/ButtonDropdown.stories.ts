import type {Meta, StoryObj} from "@storybook/vue3";
import ButtonDropdown from "../../../src/components/ButtonDropdown.vue";
import Download from "vue-material-design-icons/Download.vue";
import Pencil from "vue-material-design-icons/Pencil.vue";
import Delete from "vue-material-design-icons/Delete.vue";

const meta: Meta<typeof ButtonDropdown> = {
    title: "components/ButtonDropdown",
    component: ButtonDropdown,
    tags: ["autodocs"],
    argTypes: {
        label: {control: "text"},
        type: {control: "select", options: ["primary", "success", "warning", "danger", "info"]},
        split: {control: "boolean"},
        trigger: {control: "select", options: ["click", "hover"]},
        onClick: {action: "clicked"},
        onCommand: {action: "command"},
    },
};

export default meta;
type Story = StoryObj<typeof ButtonDropdown>;

export const Default: Story = {
    args: {
        label: "Download",
        icon: Download,
        split: true,
    },
    render: (args) => ({
        components: {ButtonDropdown, Pencil, Delete},
        setup() {
            return {args};
        },
        template: `
            <ButtonDropdown v-bind="args">
                <el-dropdown-item command="edit">
                    <Pencil class="me-2" /> Edit
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                    <Delete class="me-2" style="color: var(--bs-red);" /> Delete
                </el-dropdown-item>
            </ButtonDropdown>
        `,
    }),
};

export const UnifiedTrigger: Story = {
    args: {
        label: "More Actions",
        split: false,
    },
    render: (args) => ({
        components: {ButtonDropdown},
        setup() {
            return {args};
        },
        template: `
            <ButtonDropdown v-bind="args">
                <el-dropdown-item>Action 1</el-dropdown-item>
                <el-dropdown-item>Action 2</el-dropdown-item>
            </ButtonDropdown>
        `,
    }),
};
