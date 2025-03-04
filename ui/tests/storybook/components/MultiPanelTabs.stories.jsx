
import {defineComponent, markRaw, ref} from "vue";
import MultiPanelTabs from "../../../src/components/MultiPanelTabs.vue";
import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
import DotsSquareIcon from "vue-material-design-icons/DotsSquare.vue";
import BallotOutlineIcon from "vue-material-design-icons/BallotOutline.vue";

export default {
  title: "Components/MultiPanelTabs",
  component: MultiPanelTabs,
  argTypes: {
    panelsDefinition: {control: "object"},
  },
}


const Template = (props) => defineComponent(() => {
    const modelValueRef = ref(props.modelValue);
    return () => <div style="padding: 1rem;border: 1ps solid #ccc; border-radius: 4px; margin: 1rem; background: #f9f9f9;">
        <MultiPanelTabs modelValue={modelValueRef.value} />
        <pre>{JSON.stringify(modelValueRef.value.map(p => p.tabs.map(t => t.value)))}</pre>
    </div>
});

const BG_COLORS = [
    // lightpink
    "#FFB6C1",
    // lightblue
    "#ADD8E6",
    // lightgreen
    "#90EE90",
    // lightyellow
    "#FFFFE0",
    // lightcoral
    "#F08080",
    // lightcyan
    "#E0FFFF",
];

const PlaceholderComponent = ({tabId}) => <div style={{
    padding: "1rem",
    height: "50vh",
    background: BG_COLORS[parseInt(tabId)]
}}>Content for Tab {tabId}</div>;

export const Default = Template.bind({});
Default.args = {
  modelValue: [
    {
      activeTab: {
        button: {icon: markRaw(CodeTagsIcon), label: "Tab 1"},
        value: "tab1",
        component: () => <PlaceholderComponent tabId="1" />,
      },
      tabs: [
        {
          button: {icon: markRaw(CodeTagsIcon), label: "Tab 1"},
          value: "tab1",
          component: () => <PlaceholderComponent tabId="1" />,
        },
        {
          button: {icon: markRaw(MouseRightClickIcon), label: "Tab 2"},
          value: "tab2",
          component: () => <PlaceholderComponent tabId="2" />,
        },
        {
            button: {icon: markRaw(FileTreeOutlineIcon), label: "Tab 3"},
            value: "tab3",
            component: () => <PlaceholderComponent tabId="3" />,
        },
      ],
    },
    {
      activeTab: {
        button: {icon: markRaw(FileDocumentIcon), label: "Tab 4"},
        value: "tab4",
        component: () => <PlaceholderComponent tabId="4" />,
      },
      tabs: [

        {
          button: {icon: markRaw(FileDocumentIcon), label: "Tab 4"},
          value: "tab4",
          component: () => <PlaceholderComponent tabId="4" />,
        },
        {
          button: {icon: markRaw(DotsSquareIcon), label: "Tab 5"},
          value: "tab5",
          component: () => <PlaceholderComponent tabId="5" />,
        },
        {
          button: {icon: markRaw(BallotOutlineIcon), label: "Tab 6"},
          value: "tab6",
          component: () => <PlaceholderComponent tabId="6" />,
        },
      ],
    },
  ],
};
