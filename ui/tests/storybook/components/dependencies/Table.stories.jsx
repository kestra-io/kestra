import {ref} from "vue";
import {vueRouter} from "storybook-vue3-router";
import {FLOW} from "../../../../src/components/dependencies/utils/types";
import Table from "../../../../src/components/dependencies/components/Table.vue";
import {getDependencies} from "../../../fixtures/dependencies/getDependencies";

export default {
    title: "Dependencies/Table",
    component: Table,
    decorators: [
        vueRouter([
            {path: "/", name: "home", component: {template: "<div />"}},
            {
                path: "/flows/:namespace/:id",
                name: "flows/update",
                component: {template: "<div />"},
            },
        ]),
    ],
};

export const Default = () => ({
    components: {Table},
    setup() {
        const elements = getDependencies({subtype: FLOW});
        const selected = ref(undefined);
        const onSelect = (id) => (selected.value = id);
        return {elements, selected, onSelect};
    },
    template: `
      <div style="width:420px; height:640px;">
        <Table :elements="elements" :selected="selected" @select="onSelect" />
      </div>
    `,
});
