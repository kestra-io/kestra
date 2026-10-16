import {describe, expect, it} from "vitest"
import {filterHiddenLabels} from "./labels"

const labels = [
    {key: "internal.owner", value: "alice"},
    {key: "team", value: "data"},
]

describe("filterHiddenLabels", () => {
    it("hides labels matching configured prefixes", () => {
        expect(
            filterHiddenLabels(labels, ["internal."], {}),
        ).toEqual([
            {key: "team", value: "data"},
        ])
    })

    it("keeps a hidden label when it is explicitly filtered", () => {
        expect(
            filterHiddenLabels(
                labels,
                ["internal."],
                {"filters[labels][EQUALS][internal.owner]": "alice"},
            ),
        ).toEqual(labels)
    })

    it("supports multiple explicitly filtered labels", () => {
        expect(
            filterHiddenLabels(
                labels,
                ["internal."],
                {
                    "filters[labels][EQUALS][internal.owner]": "alice",
                    "filters[labels][EQUALS][team]": "data",
                },
            ),
        ).toEqual(labels)
    })

    it("keeps a hidden label filtered with IN", () => {
        expect(
            filterHiddenLabels(
                labels,
                ["internal."],
                {"filters[labels][IN][internal.owner]": ["alice", "bob"]},
            ),
        ).toEqual(labels)
    })

    it("keeps a hidden label hidden when it is only excluded by a filter", () => {
        for (const operation of ["NOT_EQUALS", "NOT_IN"]) {
            expect(
                filterHiddenLabels(
                    labels,
                    ["internal."],
                    {[`filters[labels][${operation}][internal.owner]`]: "alice"},
                ),
            ).toEqual([{key: "team", value: "data"}])
        }
    })
})
