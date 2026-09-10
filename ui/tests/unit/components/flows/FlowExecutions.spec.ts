import {describe, test, expect, vi} from "vitest";
import {defineComponent, h} from "vue";
import {shallowMount} from "@vue/test-utils";

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({flow: {id: "my_flow", namespace: "company.team"}}),
}));

vi.mock("../../../../src/components/executions/Executions.vue", () => ({
    default: defineComponent({
        props: {embed: {type: Boolean, default: false}},
        render: () => h("div"),
    }),
}));

import FlowExecutions from "../../../../src/components/flows/FlowExecutions.vue";
import Executions from "../../../../src/components/executions/Executions.vue";

const setup = (embed?: boolean) => {
    return shallowMount(FlowExecutions, {
        props: embed === undefined ? {} : {embed},
    });
};

describe("FlowExecutions.vue embed forwarding", () => {
    test("should forward embed to Executions so it does not overwrite the flow title", () => {
        const wrapper = setup(true);

        expect(wrapper.findComponent(Executions).props("embed")).toBe(true);
    });

    test("should leave Executions standalone when embed is not set", () => {
        const wrapper = setup();

        expect(wrapper.findComponent(Executions).props("embed")).toBeFalsy();
    });
});
