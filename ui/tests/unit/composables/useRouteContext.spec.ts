import {describe, test, expect, afterEach, beforeEach} from "vitest";
import {defineComponent, h, nextTick, ref, type Ref} from "vue";
import {mount, VueWrapper} from "@vue/test-utils";

import useRouteContext from "../../../src/composables/useRouteContext";

const setup = (routeInfo: Ref<{title: string}>, embed = false) => {
    return mount(defineComponent({
        setup() {
            useRouteContext(routeInfo, embed);
        },
        render: () => h("div")
    }));
};

describe("useRouteContext", () => {
    let wrapper: VueWrapper;

    beforeEach(() => {
        document.title = "Kestra";
    });

    afterEach(() => {
        wrapper?.unmount();
    });

    test("should set the document title on mount", () => {
        wrapper = setup(ref({title: "Flows"}));

        expect(document.title).toBe("Flows | Kestra");
    });

    test("should update the document title when the route title changes after mount", async () => {
        const routeInfo = ref({title: "Plugins"});
        wrapper = setup(routeInfo);

        routeInfo.value = {title: "io.kestra.plugin.core.log.Log"};
        await nextTick();

        expect(document.title).toBe("io.kestra.plugin.core.log.Log | Kestra");
    });

    test("should keep a single separator across repeated title changes", async () => {
        const routeInfo = ref({title: "First"});
        wrapper = setup(routeInfo);

        routeInfo.value = {title: "Second"};
        await nextTick();
        routeInfo.value = {title: "Third"};
        await nextTick();

        expect(document.title).toBe("Third | Kestra");
    });

    test("should not double the pipe when the base title starts with '|'", () => {
        document.title = "| Kestra";
        wrapper = setup(ref({title: "Default Dashboard"}));

        expect(document.title).toBe("Default Dashboard | Kestra");
    });

    test("should not print 'undefined' when the route title is missing", () => {
        wrapper = setup(ref({title: undefined as unknown as string}));

        expect(document.title).toBe("| Kestra");
    });

    test("should leave the document title untouched when embedded", () => {
        wrapper = setup(ref({title: "Flows"}), true);

        expect(document.title).toBe("Kestra");
    });
});
