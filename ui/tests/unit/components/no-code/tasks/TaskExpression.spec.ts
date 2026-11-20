import {describe, test, expect} from "vitest";
import {defineComponent} from "vue";
import {mount} from "@vue/test-utils";
import TaskExpression from "../../../../../src/components/no-code/components/tasks/TaskExpression.vue";

const EditorStub = defineComponent({
    props: {modelValue: {type: [String, Object], default: undefined}},
    emits: ["update:modelValue"],
    template: "<textarea :value='modelValue' @input='$emit(\"update:modelValue\", $event.target.value)' />",
});

function mountExpression(modelValue?: string | Record<string, any>) {
    return mount(TaskExpression, {
        props: {modelValue, root: "message"},
        global: {stubs: {Editor: EditorStub}},
    });
}

describe("TaskExpression", () => {
    test("shows a value that the parent sets after the editor mounted", async () => {
        const wrapper = mountExpression("first");
        expect(wrapper.get("textarea").element.value).toBe("first");

        await wrapper.setProps({modelValue: "second"});
        expect(wrapper.get("textarea").element.value).toBe("second");

        await wrapper.setProps({modelValue: undefined});
        expect(wrapper.get("textarea").element.value).toBe("");
    });

    test("shows a value that is not valid YAML, and the one that replaces it", async () => {
        const wrapper = mountExpression("Error: failed: x");
        expect(wrapper.get("textarea").element.value).toBe("Error: failed: x");

        await wrapper.setProps({modelValue: "other"});
        expect(wrapper.get("textarea").element.value).toBe("other");

        await wrapper.setProps({modelValue: "Error: failed: y"});
        expect(wrapper.get("textarea").element.value).toBe("Error: failed: y");
    });

    test("keeps the text being typed while the parent echoes it", async () => {
        const wrapper = mountExpression(undefined);
        const editor = wrapper.get("textarea");

        await editor.setValue("{{ inputs.message }}");
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["{{ inputs.message }}"]);
        await wrapper.setProps({modelValue: "{{ inputs.message }}"});
        expect(editor.element.value).toBe("{{ inputs.message }}");

        await editor.setValue("key: value");
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([{key: "value"}]);
        await wrapper.setProps({modelValue: {key: "value"}});
        expect(editor.element.value).toBe("key: value");
    });

    test("keeps a text that collapses to an empty value while the parent echoes it", async () => {
        const wrapper = mountExpression("first");
        const editor = wrapper.get("textarea");

        await editor.setValue("{}");
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([undefined]);
        await wrapper.setProps({modelValue: undefined});
        expect(editor.element.value).toBe("{}");
    });
});
