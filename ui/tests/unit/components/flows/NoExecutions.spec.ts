import {describe, it, expect, vi} from "vitest";
import {createI18n} from "vue-i18n";
import {shallowMount} from "@vue/test-utils";

const isAllowed = vi.fn();

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed}}),
}));
vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({flow: {id: "flow", namespace: "io.kestra.tests", deleted: false}}),
}));

import NoExecutions from "../../../../src/components/flows/NoExecutions.vue";
//@ts-expect-error no declaration file
import TriggerFlow from "../../../../src/components/flows/TriggerFlow.vue";

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}, missingWarn: false, fallbackWarn: false});

function mountNoExecutions() {
    return shallowMount(NoExecutions, {global: {plugins: [i18n], renderStubDefaultSlot: true}});
}

describe("NoExecutions", () => {
    it("shows the execute button when the user can create executions", () => {
        isAllowed.mockReturnValue(true);

        const wrapper = mountNoExecutions();

        expect(wrapper.findComponent(TriggerFlow).exists()).toBe(true);
        expect(isAllowed).toHaveBeenCalledWith("EXECUTION", "CREATE", "io.kestra.tests");
        wrapper.unmount();
    });

    it("hides the execute button when the user cannot create executions", () => {
        isAllowed.mockReturnValue(false);

        const wrapper = mountNoExecutions();

        expect(wrapper.findComponent(TriggerFlow).exists()).toBe(false);
        wrapper.unmount();
    });
});
