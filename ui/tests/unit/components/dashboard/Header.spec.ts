import {describe, test, expect, beforeEach, vi} from "vitest";
import {createI18n} from "vue-i18n";
import {shallowMount} from "@vue/test-utils";

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home"}),
}));

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => false}}),
}));

import Header from "../../../../src/components/dashboard/components/Header.vue";

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {overview: "Overview"}}, missingWarn: false, fallbackWarn: false});

const setup = (dashboard: any) => {
    return shallowMount(Header, {props: {dashboard}, global: {plugins: [i18n]}});
};

describe("dashboard Header.vue document title", () => {
    beforeEach(() => {
        document.title = "Kestra";
    });

    test("should fall back to 'Overview' when the dashboard title is an empty string", () => {
        const wrapper = setup({id: "default", title: "", deleted: false, charts: []});

        expect(document.title).toBe("Overview | Kestra");
        wrapper.unmount();
    });

    test("should use the dashboard title once it is set", () => {
        const wrapper = setup({id: "default", title: "Default Dashboard", deleted: false, charts: []});

        expect(document.title).toBe("Default Dashboard | Kestra");
        wrapper.unmount();
    });
});
