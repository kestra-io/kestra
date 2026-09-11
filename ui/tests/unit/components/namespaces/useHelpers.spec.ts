import {describe, test, expect, vi} from "vitest";
import {defineComponent, h} from "vue";
import {mount} from "@vue/test-utils";
import {createI18n} from "vue-i18n";

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {id: "company.team"}}),
}));

const {stub} = vi.hoisted(() => ({
    stub: async () => {
        const {defineComponent, h} = await import("vue");
        return {default: defineComponent({render: () => h("div")})};
    },
}));

vi.mock("../../../../src/components/flows/blueprints/BlueprintsBrowser.vue", stub);
vi.mock("../../../../src/components/flows/Flows.vue", stub);
vi.mock("../../../../src/components/executions/Executions.vue", stub);
vi.mock("../../../../src/components/dependencies/Dependencies.vue", stub);
vi.mock("../../../../src/components/namespaces/components/NamespaceFilesEditorView.vue", stub);
vi.mock("../../../../src/components/namespaces/components/NamespaceOverview.vue", stub);

import {useHelpers} from "../../../../src/components/namespaces/utils/useHelpers";

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}, missingWarn: false, fallbackWarn: false});

const setup = () => {
    let captured: ReturnType<typeof useHelpers>;
    const wrapper = mount(defineComponent({
        setup() {
            captured = useHelpers();
            return () => h("div");
        },
    }), {global: {plugins: [i18n]}});
    wrapper.unmount();
    return captured!;
};

describe("namespaces useHelpers embedded tabs", () => {
    test("should pass embed to the flows tab so it does not overwrite the namespace title", () => {
        const {tabs} = setup();
        const flowsTab = tabs.find((tab) => tab.name === "flows");

        expect(flowsTab?.props?.embed).toBe(true);
    });

    test("should pass embed to the executions tab so it does not overwrite the namespace title", () => {
        const {tabs} = setup();
        const executionsTab = tabs.find((tab) => tab.name === "executions");

        expect(executionsTab?.props?.embed).toBe(true);
    });
});
