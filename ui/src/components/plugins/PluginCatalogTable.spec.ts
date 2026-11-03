import {describe, it, expect, beforeEach, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Plugin} from "../../utils/pluginUtils"
import "../../utils/global"

vi.mock("vue-router", () => ({useRoute: () => ({params: {tenant: "main"}})}))
vi.mock("../../stores/pluginsEnrichment", () => ({
    usePluginsEnrichmentStore: () => ({getEnrichment: (plugin: Plugin) => plugin.name === "plugin-aws" ? {title: "Amazon Web Services", blueprintCount: 18} : null}),
}))

import PluginCatalogTable from "./PluginCatalogTable.vue"

const aws = {
    name: "plugin-aws",
    title: "aws",
    group: "io.kestra.plugin.aws",
    tasks: [{cls: "io.kestra.plugin.aws.s3.Upload"}, {cls: "io.kestra.plugin.aws.s3.Old", deprecated: true}],
    triggers: [{cls: "io.kestra.plugin.aws.sqs.Trigger"}],
} as Plugin
const eeSecrets = {name: "plugin-ee-secrets", title: "secrets", group: "io.kestra.plugin.ee.secrets", tasks: [{cls: "io.kestra.plugin.ee.secrets.Get"}]} as Plugin

async function mountTable(plugins: Plugin[]) {
    const wrapper = i18nMount(PluginCatalogTable, {props: {plugins}, global: {stubs: {RouterLink: {template: "<a><slot /></a>"}}}})
    await flushPromises()
    return wrapper
}

const texts = (wrapper: Awaited<ReturnType<typeof mountTable>>, test: string) =>
    wrapper.findAll(`[data-test=${test}]`).map(node => node.text())

describe("PluginCatalogTable", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("counts the non-deprecated elements and the blueprints of each plugin", async () => {
        const wrapper = await mountTable([aws, eeSecrets])

        expect(texts(wrapper, "plugin-catalog-table-tasks")).toEqual(["2", "1"])
        expect(texts(wrapper, "plugin-catalog-table-blueprints")).toEqual(["18", "0"])
    })

    it("links the release notes of a public plugin only, since an Enterprise plugin has no public repository", async () => {
        const wrapper = await mountTable([aws, eeSecrets])

        const links = wrapper.findAll("[data-test=plugin-catalog-table-release-notes]")
        expect(links.map(link => link.attributes("href"))).toEqual(["https://github.com/kestra-io/plugin-aws/releases"])
    })
})
