import {describe, test, expect, vi} from "vitest";
import {shallowMount} from "@vue/test-utils";
import {createI18n} from "vue-i18n";

vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1",
}));

vi.mock("../../../../src/utils/axios", () => ({
    useAxios: () => ({get: vi.fn().mockResolvedValue({data: {size: 0}})}),
}));

// Editor pulls Monaco in at import time, which needs browser globals jsdom does not provide.
vi.mock("../../../../src/components/inputs/Editor.vue", () => ({
    default: {props: ["modelValue"], template: "<pre data-test=\"editor\">{{ modelValue }}</pre>"},
}));

vi.mock("../../../../src/components/executions/FilePreview.vue", () => ({
    default: {template: "<div />"},
}));

import VarValue from "../../../../src/components/executions/VarValue.vue";

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
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
        template: "<button data-test=\"download\" @click=\"$emit('click')\"><slot /></button>",
    },
};

const mountValue = (value: unknown) => shallowMount(VarValue, {
    props: {value: value as object, execution: {id: "exec-1"}},
    global: {plugins: [i18n], stubs},
});

describe("VarValue oversized complex values", () => {
    test("renders a small object in the editor, with no download offered", () => {
        const value = {plays: [{name: "all", tasks: [{uid: "a", status: "ok"}]}]};

        const wrapper = mountValue(value);

        expect(wrapper.find("[data-test=alert]").exists()).toBe(false);
        expect(wrapper.find("[data-test=download]").exists()).toBe(false);
        expect(wrapper.find("[data-test=editor]").text()).toBe(JSON.stringify(value, null, 2));
    });

    // Monaco holds the whole document on the main thread: a 1 MiB output blocked the tab for
    // tens of seconds, and it can be forced open, so past the budget it is never mounted.
    test("offers a download instead of the editor past the budget", () => {
        const value = {
            plays: [{
                name: "all",
                tasks: Array.from({length: 8000}, (_, i) => ({
                    uid: `all | arcgis_access_audit : Get the credentials ${i}`,
                    name: `Get the credentials ${i}`,
                    status: "ok",
                })),
            }],
        };
        expect(JSON.stringify(value, null, 2).length).toBeGreaterThan(10 * 1024);

        const wrapper = mountValue(value);

        expect(wrapper.find("[data-test=editor]").exists()).toBe(false);
        expect(wrapper.find("[data-test=alert]").text()).toContain("too large to render here");
        expect(wrapper.find("[data-test=download]").exists()).toBe(true);
    });

    // The reported case arrives as a JSON *string*, which getDisplayValue parses before display.
    test("applies the budget to a value that arrives as a JSON string", () => {
        const value = JSON.stringify({
            plays: Array.from({length: 8000}, (_, i) => ({name: `play ${i}`, status: "ok"})),
        });

        const wrapper = mountValue(value);

        expect(wrapper.find("[data-test=editor]").exists()).toBe(false);
        expect(wrapper.find("[data-test=download]").exists()).toBe(true);
    });

    // The budget is about what reaches the DOM, so a plain string over it is no better than an
    // object: a 1 MB text node wedges layout the same way Monaco wedges the main thread.
    test("offers a download for an oversized plain string too", () => {
        const wrapper = mountValue("x".repeat(11 * 1024));

        expect(wrapper.find("[data-test=editor]").exists()).toBe(false);
        expect(wrapper.find("[data-test=download]").exists()).toBe(true);
    });

    test("renders a short plain string as text, untouched", () => {
        const wrapper = mountValue("value 2 - 0123456789");

        expect(wrapper.find("[data-test=download]").exists()).toBe(false);
        expect(wrapper.text()).toContain("value 2 - 0123456789");
    });

    test("downloads the full value as JSON, not a rendered head of it", async () => {
        const value = {items: Array.from({length: 2000}, (_, i) => `value ${i}`)};
        const serialized = JSON.stringify(value, null, 2);

        const wrapper = mountValue(value);

        let captured: Blob | undefined;
        const createObjectURL = vi.fn((blob: Blob) => {
            captured = blob;
            return "blob:mock";
        });
        vi.stubGlobal("URL", {createObjectURL, revokeObjectURL: vi.fn()});

        const anchor = document.createElement("a");
        const click = vi.spyOn(anchor, "click").mockImplementation(() => {});
        const createElement = vi.spyOn(document, "createElement")
            .mockImplementation(((tag: string) => tag === "a" ? anchor : createElement.getMockImplementation()!(tag)) as any);
        createElement.mockImplementation((tag: string) => {
            if (tag === "a") return anchor;
            return Object.getPrototypeOf(document).createElement.call(document, tag);
        });

        await wrapper.find("[data-test=download]").trigger("click");

        expect(click).toHaveBeenCalledOnce();
        expect(anchor.download).toBe("output-exec-1.json");
        expect(captured?.type).toBe("application/json");
        await expect(captured!.text()).resolves.toBe(serialized);

        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });
});
