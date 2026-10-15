import {afterEach, beforeEach, describe, expect, test} from "vitest";
import {defineComponent, h} from "vue";
import {mount, VueWrapper} from "@vue/test-utils";
import {createRouter, createMemoryHistory, type Router} from "vue-router";
import useRestoreUrl from "../../../src/composables/useRestoreUrl";

const SAVED_QUERY = {"filters[timeRange][EQUALS]": "PT24H"};

function createTestRouter(): Router {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "home", path: "/:tenant?/test", component: {template: "<div/>"}}
        ]
    });
}

describe("useRestoreUrl", () => {
    let wrapper: VueWrapper;

    beforeEach(() => {
        window.sessionStorage.clear();
        // jsdom exposes sessionStorage but not localStorage
        const store: Record<string, string> = {};
        Object.defineProperty(window, "localStorage", {
            configurable: true,
            value: {
                getItem: (key: string) => store[key] ?? null,
                setItem: (key: string, value: string) => {
                    store[key] = value;
                },
                removeItem: (key: string) => {
                    delete store[key];
                },
                clear: () => {
                    Object.keys(store).forEach((key) => delete store[key]);
                }
            } as unknown as Storage
        });
    });

    afterEach(() => {
        wrapper?.unmount();
        window.sessionStorage.clear();
    });

    const mountAt = async (router: Router) => {
        await router.push({name: "home", params: {tenant: "main"}});
        wrapper = mount(defineComponent({
            setup() {
                return useRestoreUrl();
            },
            render: () => h("div")
        }), {global: {plugins: [router]}});
        await new Promise((resolve) => setTimeout(resolve, 150));
    };

    test("restores saved filters and page size but not the page number", async () => {
        const router = createTestRouter();
        await router.push({name: "home", params: {tenant: "main"}});
        window.sessionStorage.setItem("home_main_restore_url", JSON.stringify({...SAVED_QUERY, page: "10", size: "100"}));

        wrapper = mount(defineComponent({
            setup() {
                return useRestoreUrl();
            },
            render: () => h("div")
        }), {global: {plugins: [router]}});
        await new Promise((resolve) => setTimeout(resolve, 150));

        expect(router.currentRoute.value.query).toEqual({...SAVED_QUERY, size: "100"});
    });

    test("restores the stored page size when the session holds nothing", async () => {
        const router = createTestRouter();
        window.localStorage.setItem("paginationSize__home", "50");

        await mountAt(router);

        expect(router.currentRoute.value.query).toEqual({size: "50"});
    });

    test("ignores a stored page size that is not an offered option", async () => {
        const router = createTestRouter();
        window.localStorage.setItem("paginationSize__home", "37");

        await mountAt(router);

        expect(router.currentRoute.value.query).toEqual({});
    });
});
