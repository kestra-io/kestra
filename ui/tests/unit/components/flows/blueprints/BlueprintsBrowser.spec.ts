import {describe, it, expect, vi, beforeEach} from "vitest";
import {reactive, ref} from "vue";
import {shallowMount, flushPromises} from "@vue/test-utils";
import {createI18n} from "vue-i18n";
import {createPinia} from "pinia";
import ElementPlus from "element-plus";

const getBlueprintTags = vi.fn().mockResolvedValue([]);
const getBlueprints = vi.fn().mockResolvedValue({results: [], total: 0});

// Reactive so the component's own `watch(route, ...)` has a valid source.
const route = reactive({name: "blueprints", params: {}, query: {q: "slack"}});

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
    routerKey: Symbol("router"),
}));

vi.mock("../../../../../src/stores/blueprints", () => ({
    useBlueprintsStore: () => ({getBlueprintTags, getBlueprints}),
}));

vi.mock("../../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({fetchIcons: vi.fn(), icons: {}}),
}));

// useDataTableActions only fires the initial load once useRestoreUrl reports the URL is settled.
vi.mock("../../../../../src/composables/useRestoreUrl", () => ({
    default: () => ({loadInit: ref(true), saveRestoreUrl: vi.fn(), goToRestoreUrl: vi.fn()}),
}));

import BlueprintsBrowser from "../../../../../src/components/flows/blueprints/BlueprintsBrowser.vue";

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}, missingWarn: false, fallbackWarn: false});

describe("BlueprintsBrowser", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it("filters the tag list by the search in the URL", async () => {
        shallowMount(BlueprintsBrowser, {global: {plugins: [i18n, ElementPlus, createPinia()]}});
        await flushPromises();

        expect(getBlueprintTags).toHaveBeenCalledWith(expect.objectContaining({params: {q: "slack"}}));
    });
});
