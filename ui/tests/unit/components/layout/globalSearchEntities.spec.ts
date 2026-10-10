import {describe, expect, test} from "vitest"
import {highlightMatch, paletteEntities} from "../../../../src/components/layout/globalSearchEntities"

describe("paletteEntities", () => {
    const flows = [
        {id: "daily", namespace: "analytics"},
        {id: "daily", namespace: "billing"},
    ]

    test("keeps one row per flow, with the namespace beside it and an executions destination", () => {
        const entities = paletteEntities(flows, [{id: "analytics"}], "main", () => true)

        expect(entities.map(entity => entity.destination)).toEqual(["flow", "flow", "namespace"])
        expect(entities[0]).toMatchObject({
            title: "daily",
            namespace: "analytics",
            href: {name: "flows/update", params: {tenant: "main", namespace: "analytics", id: "daily"}},
            executionsHref: {name: "flows/update/executions", params: {tenant: "main", namespace: "analytics", id: "daily"}},
        })
        expect(entities[2].href).toEqual({name: "namespaces/update", params: {tenant: "main", id: "analytics"}})
    })

    test("omits the executions destination when the user cannot view them", () => {
        const entities = paletteEntities(flows, [], undefined, namespace => namespace === "billing")

        expect(entities[0].executionsHref).toBeUndefined()
        expect(entities[1].executionsHref).toMatchObject({name: "flows/update/executions"})
        expect(entities[0].href.params).not.toHaveProperty("tenant")
    })

    test("caps each group", () => {
        const many = Array.from({length: 8}, (_, index) => ({id: `flow-${index}`, namespace: "analytics"}))
        const entities = paletteEntities(many, many.map(flow => ({id: flow.namespace + flow.id})), undefined, () => true)

        expect(entities.filter(entity => entity.destination === "flow")).toHaveLength(5)
        expect(entities.filter(entity => entity.destination === "namespace")).toHaveLength(5)
    })
})

describe("highlightMatch", () => {
    test("marks each occurrence of the query", () => {
        expect(highlightMatch("daily_active_users", "daily")).toEqual([
            {text: "daily", match: true},
            {text: "_active_users", match: false},
        ])
        expect(highlightMatch("s3_ingest_daily", "daily")).toEqual([
            {text: "s3_ingest_", match: false},
            {text: "daily", match: true},
        ])
    })
})
