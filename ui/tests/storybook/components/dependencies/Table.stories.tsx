import {ref} from "vue";
import { Meta } from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {FLOW} from "../../../../src/components/dependencies/utils/types";
import Table from "../../../../src/components/dependencies/components/Table.vue";
import {getDependencies} from "../../../fixtures/dependencies/getDependencies";

const meta: Meta<typeof Table> = {
    title: "components/dependencies/Table",
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
}

export default meta

export const Default = () => ({
    components: {Table},
    setup() {
        const elements = getDependencies({subtype: FLOW});
        const selected = ref("");
        const onSelect = (id: string) => (selected.value = id);
        return () => (
            <div style="width:420px; height:640px;">
                <Table elements={elements} selected={selected.value} onSelect={onSelect} />
            </div>
        )
    }
});
