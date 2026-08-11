import {ref} from "vue";
import TaskLabelWithBoolean from "../../../../../../src/components/no-code/components/tasks/TaskLabelWithBoolean.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";

const meta: Meta<typeof TaskLabelWithBoolean> = {
    title: "Components/NoCode/TaskLabelWithBoolean",
    component: TaskLabelWithBoolean,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskLabelWithBoolean>;

export const NotBoolean: Story = {
    render: () => ({
        setup() {
            return () => <TaskLabelWithBoolean type="string" isBoolean={false} componentProps={{}} />
        },
    }),
};

export const AsBoolean: Story = {
    render: () => ({
        setup() {
            const model = ref(false);
            return () => <div>
                <TaskLabelWithBoolean
                    type="boolean"
                    isBoolean={true}
                    componentProps={{
                        modelValue: model.value,
                        "onUpdate:modelValue": (val: boolean) => model.value = val,
                    }}
                />
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
};
