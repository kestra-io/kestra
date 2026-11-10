import {describe, test, expect} from "vitest"
import {buildDisabledFeaturesOverride, categoryLabel, ChartFeature} from "./chart"

describe("categoryLabel", () => {
    test("keeps the last segment of a fully-qualified type, with its own casing", () => {
        expect(categoryLabel("io.kestra.plugin.ee.assets.VM")).toBe("VM")
        expect(categoryLabel("io.kestra.core.models.assets.External")).toBe("External")
    })

    test("capitalises enum-shaped values", () => {
        expect(categoryLabel("SUCCESS")).toBe("Success")
        expect(categoryLabel("prod")).toBe("Prod")
    })

    test("does not trim a namespace or a decimal value", () => {
        expect(categoryLabel("io.kestra.demo")).toBe("Io.kestra.demo")
        expect(categoryLabel("10.5")).toBe("10.5")
    })
})

describe("buildDisabledFeaturesOverride", () => {
    test("a single disabled feature produces an override naming just that feature", () => {
        expect(buildDisabledFeaturesOverride([ChartFeature.LEGEND])).toEqual({
            legend: {show: false},
        })

        expect(buildDisabledFeaturesOverride([ChartFeature.TOOLTIP])).toEqual({
            tooltip: {show: false},
        })

        expect(buildDisabledFeaturesOverride([ChartFeature.AXIS])).toEqual({
            xAxis: {show: false},
            yAxis: {show: false},
            grid: {top: 2, right: 2, bottom: 2, left: 2, outerBoundsMode: "none"},
        })

        expect(buildDisabledFeaturesOverride([ChartFeature.AXIS_SPLITLINE])).toEqual({
            xAxis: {splitLine: {show: false}},
            yAxis: {splitLine: {show: false}},
        })
    })

    test("several disabled features are all present", () => {
        expect(buildDisabledFeaturesOverride([ChartFeature.LEGEND, ChartFeature.TOOLTIP])).toEqual({
            legend: {show: false},
            tooltip: {show: false},
        })

        expect(
            buildDisabledFeaturesOverride([
                ChartFeature.LEGEND,
                ChartFeature.AXIS,
                ChartFeature.TOOLTIP,
            ]),
        ).toEqual({
            legend: {show: false},
            xAxis: {show: false},
            yAxis: {show: false},
            grid: {top: 2, right: 2, bottom: 2, left: 2, outerBoundsMode: "none"},
            tooltip: {show: false},
        })

        expect(
            buildDisabledFeaturesOverride([
                ChartFeature.AXIS,
                ChartFeature.AXIS_SPLITLINE,
            ]),
        ).toEqual({
            xAxis: {show: false, splitLine: {show: false}},
            yAxis: {show: false, splitLine: {show: false}},
            grid: {top: 2, right: 2, bottom: 2, left: 2, outerBoundsMode: "none"},
        })
    })

    test("an empty list produces an override that disables nothing", () => {
        expect(buildDisabledFeaturesOverride([])).toEqual({})
    })

    test("an unknown feature name does not throw", () => {
        expect(() =>
            buildDisabledFeaturesOverride(["UNKNOWN_FEATURE" as ChartFeature]),
        ).not.toThrow()
        expect(
            buildDisabledFeaturesOverride(["UNKNOWN_FEATURE" as ChartFeature]),
        ).toEqual({})
    })

    test("the returned object is new each call rather than a shared mutable one", () => {
        const first = buildDisabledFeaturesOverride([ChartFeature.LEGEND])
        const second = buildDisabledFeaturesOverride([ChartFeature.LEGEND])

        expect(first).toEqual(second)
        expect(first).not.toBe(second)

        // Mutating one returned object does not affect another or subsequent calls
        first.legend = {show: true}
        expect(second.legend).toEqual({show: false})
        expect(buildDisabledFeaturesOverride([ChartFeature.LEGEND])).toEqual({
            legend: {show: false},
        })
    })

    test("handles multi-axis baseOption when disabling axis features", () => {
        const baseOption = {
            yAxis: [
                {type: "value", name: "Primary"},
                {type: "value", name: "Secondary"},
            ],
        }

        const override = buildDisabledFeaturesOverride(
            [ChartFeature.AXIS_SPLITLINE],
            baseOption,
        )

        expect(override.yAxis).toEqual([
            {type: "value", name: "Primary", splitLine: {show: false}},
            {type: "value", name: "Secondary", splitLine: {show: false}},
        ])
    })
})
