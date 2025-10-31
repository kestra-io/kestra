import {ref} from "vue";
import {FLOW, type Node} from "../../../../src/components/dependencies/utils/types";
import Table from "../../../../src/components/dependencies/components/Table.vue";
import {getDependencies} from "../../../fixtures/dependencies/getDependencies";

export default {
    title: "Components/Dependencies/Table",
    component: Table,
};

const Template = () => ({
    components: {Table},
    setup() {
        const elements = getDependencies({subtype: FLOW});
        const selected = ref<Node["id"] | undefined>(undefined);
        const onSelect = (id: Node["id"]) => selected.value = id;
        return {elements, selected, onSelect};
    },
    template: `
      <div style="width:420px; height:640px;">
        <Table :elements="elements" :selected="selected" @select="onSelect" />
      </div>
    `,
});

export const Default = Template.bind({});


