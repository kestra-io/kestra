import {describe, expect, it, vi} from "vitest"
import {useDagGrouping} from "../../../../src/components/dependencies/composables/useDagGrouping"
import {ASSET, FLOW} from "../../../../src/components/dependencies/utils/types"
import type {Edge, Node} from "../../../../src/components/dependencies/utils/types"

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

const asset = (id: string): Node => ({id, type: "NODE", flow: id, namespace: undefined, metadata: {subtype: ASSET}}) as Node
const flow = (id: string, namespace?: string): Node => ({id, type: "NODE", flow: id, namespace, metadata: {subtype: FLOW}}) as Node
const consumedBy = (source: string, target: string): Edge => ({id: `${source}-${target}`, type: "EDGE", source, target, kind: "CONSUMED_BY"}) as Edge

const nodes = [asset("orders"), asset("customers"), asset("raw"), flow("dbt", "prod"), flow("export", "prod"), flow("hidden")]
const edges = [consumedBy("orders", "dbt"), consumedBy("orders", "export"), consumedBy("customers", "dbt")]

describe("useDagGrouping by consumer", () => {
    it("lists a consumer field only when the graph has consumed-by edges", () => {
        expect(useDagGrouping(() => nodes, () => edges).groupFields.value.map((field) => field.key)).toContain("consumer")
        expect(useDagGrouping(() => nodes, () => []).groupFields.value.map((field) => field.key)).not.toContain("consumer")
    })

    it("puts an asset read by two flows in both groups", () => {
        const grouping = useDagGrouping(() => nodes, () => edges)
        grouping.groupField.value = "consumer"

        const members = grouping.membersOf.value!
        expect(members(nodes[0])).toEqual(["dbt", "export"])
        expect(members(nodes[2])).toEqual([""])
        expect(grouping.groupChips.value).toEqual([
            {key: "dbt", count: 2, label: "prod.dbt"},
            {key: "export", count: 1, label: "prod.export"},
            {key: "", count: 1, label: "dependency.dag.no_consumer"},
        ])
    })

    it("never re-lanes: the grouping has no single key and no priority", () => {
        const grouping = useDagGrouping(() => nodes, () => edges)
        grouping.groupField.value = "consumer"

        expect(grouping.groupOf.value).toBeUndefined()
        expect(grouping.dagPriority.value).toBeUndefined()
    })

    it("labels a flow the caller cannot see as hidden", () => {
        const grouping = useDagGrouping(() => nodes, () => [consumedBy("raw", "hidden")])
        grouping.groupField.value = "consumer"

        expect(grouping.groupChips.value[0].label).toBe("dependency.dag.hidden_consumer")
    })
})
