import {describe, expect, it, beforeEach} from "vitest"
import {defineComponent, h} from "vue"
import {createPinia, setActivePinia} from "pinia"

import AssetNode from "../../../../src/components/dependencies/components/dag/AssetNode.vue"
import {DAG_CARD_FOOTER} from "../../../../src/components/dependencies/utils/dagConstants"
import en from "../../../../src/translations/en.json"
import {i18nMount} from "../../i18nMount"

const Footer = defineComponent({
    props: ["taskType", "assetId", "assetType", "metadata"],
    setup: (props) => () => h("div", {class: "plugin-footer"}, `${props.taskType}|${props.assetId}|${JSON.stringify(props.metadata)}`),
})

function mountCard(data: Record<string, unknown> = {}, footer?: unknown) {
    return i18nMount(AssetNode, {
        props: {
            id: "warehouse.dim_customers",
            data: {name: "dim_customers", status: "fresh", updated: "2026-09-24T08:00:00Z", ...data},
        },
        locales: en,
        global: {
            stubs: {Handle: true},
            provide: footer ? {[DAG_CARD_FOOTER as symbol]: footer} : {},
        },
    })
}

describe("AssetNode plugin footer", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("renders nothing when no footer artifact is provided", () => {
        expect(mountCard({producer: "io.kestra.plugin.dbt.cli.DbtCLI"}).find(".plugin-footer").exists()).toBe(false)
    })

    it("renders nothing for an asset without a producer", () => {
        expect(mountCard({}, Footer).find(".plugin-footer").exists()).toBe(false)
    })

    it("hands the producer, asset id and metadata to the footer", () => {
        const footer = mountCard({producer: "io.kestra.plugin.dbt.cli.DbtCLI", metadata: {rows: 3}}, Footer).find(".plugin-footer")

        expect(footer.text()).toBe("io.kestra.plugin.dbt.cli.DbtCLI|warehouse.dim_customers|{\"rows\":3}")
    })
})
