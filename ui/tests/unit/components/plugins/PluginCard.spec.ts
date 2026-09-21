import {describe, test, expect} from "vitest"
import {h} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import PluginCard from "../../../../src/components/plugins/PluginCard.vue"
import {i18nMount} from "../../i18nMount"

const messages = {
    plugin_card: {
        tasks: "task | tasks",
        blueprints: "blueprint | blueprints",
    },
}

const globalConfig = {plugins: [KestraDesignSystem]}

describe("PluginCard", () => {
    test("renders title and description", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", description: "Query data."},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__title").text()).toBe("BigQuery")
        expect(wrapper.find(".plugin-card__description").text()).toBe("Query data.")
    })

    test("omits description block when not provided", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__description").exists()).toBe(false)
    })

    test("renders categories as tags", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", categories: ["DATABASE", "CLOUD"]},
            global: globalConfig,
        })
        const tags = wrapper.find(".plugin-card__tags")
        expect(tags.exists()).toBe(true)
        expect(tags.text()).toContain("DATABASE")
        expect(tags.text()).toContain("CLOUD")
        expect(wrapper.findAll(".plugin-card__category")).toHaveLength(2)
    })

    test("omits tags block when categories empty", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", categories: []},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__tags").exists()).toBe(false)
    })

    test("renders task count when provided", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", taskCount: 12},
            global: globalConfig,
        })
        const counts = wrapper.findAll(".plugin-card__count")
        expect(counts).toHaveLength(1)
        expect(counts[0].find(".plugin-card__count-value").text()).toBe("12")
        expect(counts[0].find(".plugin-card__count-label").text()).toBeTruthy()
    })

    test("renders distinct labels for task and blueprint counts", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", taskCount: 1, blueprintCount: 1},
            global: globalConfig,
        })
        const labels = wrapper.findAll(".plugin-card__count-label")
        expect(labels).toHaveLength(2)
        expect(labels[0].text()).not.toBe(labels[1].text())
        expect(labels[0].text()).toBeTruthy()
        expect(labels[1].text()).toBeTruthy()
    })

    test("hides count when value is 0", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", taskCount: 0, blueprintCount: 0},
            global: globalConfig,
        })
        expect(wrapper.findAll(".plugin-card__count")).toHaveLength(0)
    })

    test("renders task and blueprint counts together", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", taskCount: 12, blueprintCount: 4},
            global: globalConfig,
        })
        expect(wrapper.findAll(".plugin-card__count")).toHaveLength(2)
    })

    test("omits footer when no counts and not clickable", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", clickable: false},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__footer").exists()).toBe(false)
        expect(wrapper.find(".plugin-card__divider").exists()).toBe(false)
    })

    test("emits click when clickable and clicked", async () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        await wrapper.find(".plugin-card").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("does not emit click when not clickable", async () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", clickable: false},
            global: globalConfig,
        })
        await wrapper.find(".plugin-card").trigger("click")
        expect(wrapper.emitted("click")).toBeFalsy()
    })

    test("applies clickable class when clickable", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card").classes()).toContain("plugin-card--clickable")
    })

    test("renders icon block when iconCls provided", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery", iconCls: "io.kestra.plugin.gcp.bigquery"},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__logo").exists()).toBe(true)
    })

    test("renders icon block when #icon slot provided (without iconCls)", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery"},
            slots: {icon: () => h("svg", {class: "custom-icon"})},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__logo").exists()).toBe(true)
        expect(wrapper.find(".custom-icon").exists()).toBe(true)
    })

    test("omits icon block when no iconCls and no icon slot", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        expect(wrapper.find(".plugin-card__logo").exists()).toBe(false)
    })

    test("chevron is purely decorative (not a button)", () => {
        const wrapper = i18nMount(PluginCard, {
            messages,
            props: {title: "BigQuery"},
            global: globalConfig,
        })
        const chevron = wrapper.find(".plugin-card__chevron")
        expect(chevron.exists()).toBe(true)
        expect(chevron.attributes("aria-hidden")).toBe("true")
        expect(wrapper.find(".plugin-card__chevron[role='button']").exists()).toBe(false)
    })
})
