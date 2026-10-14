import {computed, provide, ref} from "vue";
import TaskComplex from "./TaskComplex.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {
    SCHEMA_DEFINITIONS_INJECTION_KEY,
    FULL_SCHEMA_INJECTION_KEY,
} from "../../injectionKeys";

const meta: Meta<typeof TaskComplex> = {
    title: "Components/NoCode/TaskComplex",
    component: TaskComplex,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskComplex>;

export const WithProperties: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            provide(FULL_SCHEMA_INJECTION_KEY, ref({definitions: {}, $ref: ""}));
            const model = ref<Record<string, unknown>>({});
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskComplex
                        schema={args.schema}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        schema: {
            type: "object",
            properties: {
                host: {type: "string", title: "Hostname"},
                port: {type: "number", title: "Port number"},
                ssl: {type: "boolean", title: "Use SSL"},
            },
            required: ["host"],
        },
    },
};

export const WithRefSchema: Story = {
    render: (args) => ({
        setup() {
            const definitions = {
                ConnectionConfig: {
                    type: "object",
                    properties: {
                        host: {type: "string", title: "Hostname"},
                        port: {type: "number", title: "Port"},
                    },
                    required: ["host"],
                },
            };
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => definitions));
            provide(FULL_SCHEMA_INJECTION_KEY, ref({definitions, $ref: ""}));
            const model = ref<Record<string, unknown>>({host: "localhost"});
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskComplex
                        schema={args.schema}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        schema: {
            $ref: "#/definitions/ConnectionConfig",
        },
    },
};
