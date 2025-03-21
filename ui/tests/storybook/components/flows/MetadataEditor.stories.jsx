import MetadataEditor from "../../../../src/components/flows/MetadataEditor.vue";

export default {
    title: "Components/Flows/MetadataEditor",
    component: MetadataEditor,
    argTypes: {
        metadata: {control: "object"},
        editing: {control: "boolean"},
    },
}

const Template = (args) => ({
    components: {MetadataEditor},
    setup() {
        return () => <div style="margin: 1rem; width: 400px;border: 1px solid lightgray; padding: .5rem;">
            <MetadataEditor {...args} />
        </div>;
    }
});

export const Default = Template.bind({});
Default.args = {
    metadata: {
        id: "sample",
        namespace: "test",
        description: "Sample description",
        retry: "",
        labels: {key: "value"},
        inputs: [],
        variables: {var1: "value1"},
        concurrency: {},
        pluginDefaults: "",
        outputs: "",
        disabled: false,
    },
    editing: true,
};
