import {describe, test, expect} from "vitest"
import {h} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import PropertyDetail from "./PropertyDetail.vue"

describe("PropertyDetail", () => {
    test("renders value row labels through i18n", () => {
        const wrapper = i18nMount(PropertyDetail, {
            props: {property: {type: "integer", default: "5", unit: "ms", minimum: 1, exclusiveMinimum: 0, maximum: 10, exclusiveMaximum: 11, format: "int32"}},
        })

        const rows = wrapper.findAll(".property-detail > div").map((row) => [row.find("span").text(), row.find("code").text()])
        expect(rows).toEqual([
            ["default", "5"],
            ["plugins.unit", "ms"],
            ["plugins.minimum", ">= 1"],
            ["plugins.minimum", "> 0"],
            ["plugins.maximum", "<= 10"],
            ["plugins.maximum", "< 11"],
            ["plugins.format", "int32"],
        ])
    })

    test("renders the internal storage URI hint through i18n with the Pebble example", () => {
        const wrapper = i18nMount(PropertyDetail, {
            props: {property: {type: "string", title: "The file to delete.", $internalStorageURI: true}},
            messages: {plugins: {internal_storage_uri_hint: "Hint {example}"}},
            slots: {markdown: ({content}: {content: string}) => h("div", {class: "rendered-markdown"}, content)},
        })

        expect(wrapper.findAll(".rendered-markdown").map((node) => node.text())).toEqual(["The file to delete.", "Hint `{{ outputs.mytask.uri }}`"])
    })
})
