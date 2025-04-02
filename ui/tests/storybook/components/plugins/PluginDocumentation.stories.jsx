
import {useStore} from "vuex";
import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue";

export default {
    title: "Components/Plugins/PluginDocumentation",
    component: PluginDocumentation,
    argTypes: {
        overrideIntro: {control: "text"},
    },
};

const Template = (args) => ({
    setup() {
        const store = useStore()
        store.$http = {
            get(){
                return  Promise.resolve({data: []})
            }
        }
        return () => <PluginDocumentation {...args} />
    }
});

export const Default = Template.bind({});
Default.args = {
    overrideIntro: "This is an overridden intro content.",
};
