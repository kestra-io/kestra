import {describe, it, expect} from "vitest"
import {chartDataType} from "./chartDataType"

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"
const fullSchema = {
    definitions: {
        ExecutionsData: {properties: {type: {const: EXECUTIONS}}},
    },
}

describe("chartDataType", () => {
    it("reads the data source of a chart whose data is a bare $ref", () => {
        const chart = {properties: {data: {$ref: "#/definitions/ExecutionsData"}}}

        expect(chartDataType(chart, fullSchema)).toBe(EXECUTIONS)
    })

    it("reads the data source of a chart whose data carries a title next to its $ref", () => {
        const chart = {properties: {data: {allOf: [{$ref: "#/definitions/ExecutionsData"}, {title: "Data source the chart queries."}]}}}

        expect(chartDataType(chart, fullSchema)).toBe(EXECUTIONS)
    })
})
