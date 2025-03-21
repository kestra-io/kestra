import TaskEditor from "../../../../src/components/flows/TaskEditor.vue";

export default {
    title: "Components/Flows/TaskEditor",
    component: TaskEditor,
    argTypes: {
        modelValue: {control: "text"},
        section: {control: "text"},
    },
}

const Template = (args) => ({
    components: {TaskEditor},
    setup() {
        return () => <TaskEditor {...args} />
    }
});

export const Default = Template.bind({});
Default.args = {
    modelValue: `
    id: task1
    type: taskType
    `,
    section: "tasks",
};
