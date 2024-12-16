import useStore from "vuex";
import EditorSidebar from "./EditorSidebar.vue";

const meta = {
    title: "inputs/EditorSidebar",
    component: EditorSidebar,
}

export default meta;

export const Default = () => ({
    setup() {
        const store = useStore()
        return { };
    },
    render: () => <EditorSidebar currentNS="example"/>
});