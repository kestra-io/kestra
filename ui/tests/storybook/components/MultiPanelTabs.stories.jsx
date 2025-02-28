/* eslint-disable vue/one-component-per-file */
import {defineComponent} from "vue";
import MultiPanelTabs from "../../../src/components/MultiPanelTabs.vue";

export default {
  title: "Components/MultiPanelTabs",
  component: MultiPanelTabs,
  argTypes: {
    panelsDefinition: {control: "object"},
  },
}


const Template = (args) => defineComponent({
  setup() {
    return () => <MultiPanelTabs {...args} />;
  },
});

export const Default = Template.bind({});
Default.args = {
  panelsDefinition: [
    {
      tabs: [
        {
          button: {icon: "icon1", label: "Tab 1"},
          value: "tab1",
          component: () => <div style="padding: 1rem">Content for Tab 1</div>,
        },
        {
          button: {icon: "icon2", label: "Tab 2"},
          value: "tab2",
          component: () => <div style="padding: 1rem">Content for Tab 2</div>,
        },
      ],
    },
    {
      tabs: [
        {
          button: {icon: "icon3", label: "Tab 3"},
          value: "tab3",
          component: () => <div style="padding: 1rem">Content for Tab 3</div>,
        },
      ],
    },
  ],
};
