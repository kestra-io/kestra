import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue";

export default {
    title: "Components/Plugins/PluginDocumentation",
    component: PluginDocumentation,
    argTypes: {
        overrideIntro: {control: "text"},
    },
};

const Template = (args) => ({
    components: {PluginDocumentation},
    setup() {
        return () => <PluginDocumentation {...args} />
    }
});

export const Default = Template.bind({});
Default.args = {
    overrideIntro: "This is an overridden intro content.",
};
