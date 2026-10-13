import {describe, test, expect} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import PropertyDetail from "./PropertyDetail.vue"

describe("PropertyDetail", () => {
    test("renders value row labels through i18n", () => {
        const wrapper = i18nMount(PropertyDetail, {
            props: {property: {type: "integer", default: "5", minimum: 1, exclusiveMinimum: 0}},
        })

        const rows = wrapper.findAll(".property-detail > div").map((row) => [row.find("span").text(), row.find("code").text()])
        expect(rows).toEqual([
            ["default", "5"],
            ["plugins.minimum", ">= 1"],
            ["plugins.minimum", "> 0"],
        ])
    })
})
