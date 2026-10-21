import {describe, it, expect, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";

vi.mock("../../../../src/stores/plugins", () => ({usePluginsStore: () => ({plugin: undefined, load: vi.fn()})}));
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: undefined})}));
vi.mock("../../../../src/stores/flow", () => ({useFlowStore: () => ({taskError: undefined, flow: undefined, validateTask: vi.fn()})}));
vi.mock("../../../../src/components/Drawer.vue", () => ({
    default: {name: "Drawer", template: "<div><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>"},
}));
vi.mock("../../../../src/components/no-code/components/TaskEditor.vue", () => ({
    default: {name: "TaskEditor", props: ["modelValue", "section"], template: "<div />"},
}));
vi.mock("../../../../src/components/inputs/Editor.vue", () => ({
    default: {name: "Editor", props: ["modelValue", "schemaType"], template: "<div data-test=\"editor\" />"},
}));

import TaskEdit from "../../../../src/components/flows/TaskEdit.vue";

async function openedEditor(section: string) {
    const wrapper = mount(TaskEdit, {
        props: {flowId: "flow", namespace: "ns", section, task: {id: "some_task", type: "io.kestra.plugin.core.log.Log"}},
        global: {
            mocks: {$t: (key: string) => key},
            directives: {loading: () => undefined},
            stubs: {
                "el-button": {emits: ["click"], template: "<button @click=\"$emit('click')\"><slot /></button>"},
                "el-tabs": {template: "<div><slot /></div>"},
                "el-tab-pane": {template: "<div><slot name=\"label\" /><slot /></div>"},
                "el-alert": true,
                ValidationError: true,
                Markdown: true,
            },
        },
    });
    await wrapper.find("button").trigger("click");
    await flushPromises();
    return wrapper.findComponent({name: "Editor"});
}

describe("TaskEdit", () => {
    it.each([
        ["TASKS", "task"],
        ["TRIGGERS", "trigger"],
    ])("declares the schema type the %s section is bound to on its Source editor", async (section, schemaType) => {
        // Monaco names the model `<schemaType>-<path>.yaml`, and `yamlSchemas` only binds `task-*` and `trigger-*`
        expect((await openedEditor(section)).props("schemaType")).toBe(schemaType);
    });
});
