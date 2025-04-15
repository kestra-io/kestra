import {useStore} from "vuex";
import {vueRouter} from "storybook-vue3-router";
import LowCodeEditor from "../../../../src/components/inputs/LowCodeEditor.vue";

export default {
    title: "Components/Inputs/LowCodeEditor",
    component: LowCodeEditor,
    decorators: [vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
        ])]
};

const Template= (args) => ({
    setup() {
        const store = useStore()
        store.$http = {
            get(){
                return  Promise.resolve({data: {}})
            }
        }
        return () => <div style="width:600px; height:600px;"><LowCodeEditor {...args} /></div>;
    }
});

export const Default = Template.bind({});
Default.args = {
    flowGraph: {
        nodes: []
    },
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
