import {describe, test, expect, vi} from "vitest";
import {shallowMount, flushPromises} from "@vue/test-utils";
import {createI18n} from "vue-i18n";

const post = vi.fn();

vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1",
}));

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}));

vi.mock("../../../../../src/stores/flow", () => ({
    useFlowStore: () => ({flowYaml: "id: f", flowYamlOrigin: "id: f", loadFlow: vi.fn()}),
}));

vi.mock("../../../../../src/utils/axios", () => ({
    useAxios: () => ({post, get: vi.fn().mockResolvedValue({data: {size: 0}})}),
}));

// Editor pulls Monaco in at import time, which needs browser globals jsdom does not provide.
vi.mock("../../../../../src/components/inputs/Editor.vue", () => ({
    default: {props: ["modelValue"], template: "<pre />"},
}));

vi.mock("../../../../../src/components/executions/FilePreview.vue", () => ({
    default: {template: "<div />"},
}));

import DebugPanel from "../../../../../src/components/executions/overview/components/main/cascaders/DebugPanel.vue";

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            eval: {render: "Render"},
            large_outputs: {
                value_too_large: "This value is {size}. It is too large to render here, download it instead.",
                download_json: "Download JSON",
            },
        },
    },
    missingWarn: false,
    fallbackWarn: false,
});

// shallowMount auto-stubs children, so these explicit stubs are what actually renders.
const stubs = {
    Editor: {props: ["modelValue"], template: "<pre data-test=\"editor\">{{ modelValue }}</pre>"},
    "el-alert": {template: "<div data-test=\"alert\"><slot /></div>"},
    // emits declared so the parent's @click is not also attached as a native fallthrough.
    "el-button": {
        emits: ["click"],
        template: "<button @click=\"$emit('click')\"><slot /></button>",
    },
};

const render = async (result: string) => {
    post.mockResolvedValue({status: 200, data: {result}});

    const wrapper = shallowMount(DebugPanel, {
        props: {execution: {id: "exec-1", namespace: "ns", flowId: "f"} as any, property: "outputs"},
        global: {plugins: [i18n], stubs},
    });

    await wrapper.findAll("button")[0].trigger("click");
    await flushPromises();

    return wrapper;
};

describe("overview DebugPanel result", () => {
    test("renders a small result in the editor", async () => {
        const wrapper = await render(JSON.stringify({ok: true}));

        expect(wrapper.find("[data-test=alert]").exists()).toBe(false);
        // the first editor is the expression box, the second is the result
        expect(wrapper.findAll("[data-test=editor]")[1].text()).toContain("\"ok\": true");
    });

    // Evaluating an expression over a whole task's outputs froze the tab: the result went
    // into a second editor of its own, with no budget on it.
    test("offers a download instead of the editor past the budget", async () => {
        const wrapper = await render(JSON.stringify(
            Object.fromEntries(Array.from({length: 400}, (_, i) => [`item_${i}`, "0123456789".repeat(6)])),
        ));

        expect(wrapper.find("[data-test=alert]").text()).toContain("too large to render");
        expect(wrapper.text()).toContain("Download JSON");
        expect(wrapper.findAll("[data-test=editor]")).toHaveLength(1);
    });

    test("applies the budget to a plain-text result too", async () => {
        const wrapper = await render("not json ".repeat(2000));

        expect(wrapper.find("[data-test=alert]").text()).toContain("too large to render");
    });
});
