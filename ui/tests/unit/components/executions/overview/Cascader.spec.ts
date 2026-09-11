import {describe, test, expect, vi} from "vitest";
import {shallowMount} from "@vue/test-utils";
import {createI18n} from "vue-i18n";

vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1",
}));

vi.mock("../../../../../src/utils/axios", () => ({
    useAxios: () => ({get: vi.fn().mockResolvedValue({data: {size: 0}})}),
}));

// Editor pulls Monaco in at import time, which needs browser globals jsdom does not provide.
vi.mock("../../../../../src/components/inputs/Editor.vue", () => ({
    default: {props: ["modelValue"], template: "<pre />"},
}));

vi.mock("../../../../../src/components/executions/FilePreview.vue", () => ({
    default: {template: "<div />"},
}));

import Cascader from "../../../../../src/components/executions/overview/components/main/cascaders/Cascader.vue";

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            item: "item",
            items: "items",
            search: "Search",
            large_outputs: {
                load_more: "Load {count} more ({remaining} left)",
                value_too_large: "This value is {size}.",
                download_json: "Download JSON",
            },
        },
    },
    missingWarn: false,
    fallbackWarn: false,
});

// shallowMount auto-stubs children, so the panel stub is what exposes the built options.
const stubs = {
    "el-cascader-panel": {
        props: ["options"],
        template: "<div data-test=\"panel\">{{ JSON.stringify(options) }}</div>",
    },
};

const options = (elements: Record<string, any>) => {
    const wrapper = shallowMount(Cascader, {
        props: {title: "Flow Outputs", empty: "none", elements, execution: {id: "exec-1"} as any},
        global: {plugins: [i18n], stubs},
    });

    return JSON.parse(wrapper.find("[data-test=panel]").text());
};

describe("overview Cascader", () => {
    test("keeps a small tree whole and hangs scalars off their key", () => {
        const nodes = options({outer: {inner: "value"}});

        expect(nodes).toHaveLength(1);
        expect(nodes[0].total).toBe(1);
        expect(nodes[0].children[0].children[0]).toMatchObject({label: "value", value: "value"});
    });

    // format() built one node per value and the column painted in one go, which froze the
    // execution's default tab with no click at all.
    test("pages a wide level behind a load-more row", () => {
        const wide = Object.fromEntries(Array.from({length: 15000}, (_, i) => [`item_${i}`, `v${i}`]));

        const nodes = options({big: wide});
        const level = nodes[0].children;

        expect(level).toHaveLength(201);
        expect(level[200]).toMatchObject({loadMore: true, disabled: true, path: "big"});
        expect(level[200].label).toBe("Load 200 more (14800 left)");
    });

    test("reports the real key count on the parent, not the number of nodes built", () => {
        const wide = Object.fromEntries(Array.from({length: 15000}, (_, i) => [`item_${i}`, `v${i}`]));

        expect(options({big: wide})[0].total).toBe(15000);
    });

    // A value past the budget is its own freeze: the column rendered it as one text node.
    test("offers an oversized value as a download instead of a text node", () => {
        const leaf = options({blob: "x".repeat(11 * 1024)})[0].children[0];

        expect(leaf.tooLarge).toBe(true);
        expect(leaf.size).toBe("11.0 KiB");
        expect(leaf.value).toHaveLength(11 * 1024);
    });

    test("leaves a value under the budget inline", () => {
        const leaf = options({blob: "x".repeat(9 * 1024)})[0].children[0];

        expect(leaf.tooLarge).toBeUndefined();
        expect(leaf.label).toHaveLength(9 * 1024);
    });
});
