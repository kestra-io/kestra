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
    });

    afterEach(() => {
        wrapper?.unmount();
        window.sessionStorage.clear();
    });

    test("restores saved filters but not pagination", async () => {
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

        expect(router.currentRoute.value.query).toEqual(SAVED_QUERY);
    });
});
