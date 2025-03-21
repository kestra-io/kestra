import LowCodeEditor from "../../../../src/components/inputs/LowCodeEditor.vue";

export default {
    title: "Components/Inputs/LowCodeEditor",
    component: LowCodeEditor,
};

const Template= (args) => ({
    components: {LowCodeEditor},
    setup() {
        return () => <LowCodeEditor {...args} />;
    }
});

export const Default = Template.bind({});
Default.args = {
    flowGraph: {},
    flowId: "flow1",
    namespace: "namespace1",
    execution: {},
    isReadOnly: false,
    source: `
    id: flow1
    namespace: namespace1
    tasks:
      - id: task1
        type: taskType
    `,
    isAllowedEdit: true,
    viewType: "default",
    expandedSubflows: [],
};
