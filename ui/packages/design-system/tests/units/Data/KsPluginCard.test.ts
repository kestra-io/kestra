import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsPluginCard from "../../../src/components/Data/KsPluginCard.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            pluginCard_tasks: "tasks",
            pluginCard_blueprints: "blueprints",
        },
    },
})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("KsPluginCard", () => {
    test("renders title and description", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", description: "Query data."},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card__title").text()).toBe("BigQuery")
        expect(wrapper.find(".ks-plugin-card__description").text()).toBe("Query data.")
    })

    test("omits description block when not provided", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card__description").exists()).toBe(false)
    })

    test("renders categories as tags", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", categories: ["DATABASE", "CLOUD"]},
            global: globalConfig,
        })
        const tags = wrapper.find(".ks-plugin-card__tags")
        expect(tags.exists()).toBe(true)
        expect(tags.text()).toContain("Database")
        expect(tags.text()).toContain("Cloud")
    })

    test("omits tags block when categories empty", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", categories: []},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card__tags").exists()).toBe(false)
    })

    test("renders task count when provided", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", taskCount: 12},
            global: globalConfig,
        })
        const counts = wrapper.findAll(".ks-plugin-card__count")
        expect(counts).toHaveLength(1)
        expect(counts[0].find(".ks-plugin-card__count-value").text()).toBe("12")
    })

    test("renders task and blueprint counts together", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", taskCount: 12, blueprintCount: 4},
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-plugin-card__count")).toHaveLength(2)
    })

    test("omits footer when no counts and not clickable", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", clickable: false},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card__footer").exists()).toBe(false)
        expect(wrapper.find(".ks-plugin-card__divider").exists()).toBe(false)
    })

    test("emits click when clickable and clicked", async () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        await wrapper.find(".ks-plugin-card").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("does not emit click when not clickable", async () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", clickable: false},
            global: globalConfig,
        })
        await wrapper.find(".ks-plugin-card").trigger("click")
        expect(wrapper.emitted("click")).toBeFalsy()
    })

    test("applies clickable class when clickable", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card").classes()).toContain("ks-plugin-card--clickable")
    })

    test("renders icon when iconCls provided", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery", iconCls: "io.kestra.plugin.gcp.bigquery"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card__logo").exists()).toBe(true)
    })

    test("omits icon block when no iconCls and no icon slot", () => {
        const wrapper = mount(KsPluginCard, {
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-plugin-card__logo").exists()).toBe(false)
    })
})
