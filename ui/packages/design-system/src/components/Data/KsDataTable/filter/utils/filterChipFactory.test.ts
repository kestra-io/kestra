import {describe, expect, test} from "vitest"

import {
    Comparators,
    isLeafGroup,
    type AppliedFilter,
    type FilterConfiguration,
    type FilterKeyConfig,
} from "./filterTypes"
import {
    buildNewFilter,
    createAppliedFilter,
    createCustomRangeFilter,
    createDefaultVisibleFilters,
    createTimeRangeFilter,
    defaultVisibleValueLabel,
    isDateRangeValue,
    processFieldValue,
    resolveDefaultVisibleValue,
} from "./filterChipFactory"
import {parseEncodedGroups} from "./routeDecoder"
import {DATE_FILTER_KEY, TIME_RANGE_KEY} from "./constants"

const labelsConfiguration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "labels",
            label: "Labels",
            valueType: "key-value",
            comparators: [
                Comparators.IN,
                Comparators.NOT_IN,
                Comparators.EQUALS,
                Comparators.NOT_EQUALS,
                Comparators.CONTAINS,
                Comparators.NOT_CONTAINS,
                Comparators.IS_NOT_NULL,
                Comparators.IS_NULL,
            ],
        },
    ],
}

describe("filterChipFactory", () => {
    test.each([
        [Comparators.CONTAINS, "CONTAINS"],
        [Comparators.NOT_CONTAINS, "NOT_CONTAINS"],
    ])(
        "restores labels %s text values as strings",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}][team]`]: "core",
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: "team:core",
                valueLabel: "team:core",
            })
        },
    )

    test.each([
        [Comparators.IN, "IN"],
        [Comparators.NOT_IN, "NOT_IN"],
    ])(
        "restores labels %s key-value values as arrays",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}][team]`]: "core",
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: ["team:core"],
                valueLabel: "team:core",
            })
        },
    )

    test.each([
        [Comparators.IN, "IN"],
        [Comparators.NOT_IN, "NOT_IN"],
    ])(
        "restores repeated labels %s values after a URL reload",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}][environment]`]: ["production", "staging"],
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: ["environment:production", "environment:staging"],
                valueLabel: "environment:production +1",
            })
        },
    )

    test.each([
        [Comparators.IS_NOT_NULL, "IS_NOT_NULL"],
        [Comparators.IS_NULL, "IS_NULL"],
    ])(
        "restores labels %s key-only values as strings",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}]`]: "team",
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: "team",
                valueLabel: "team",
            })
        },
    )
})

const timeRangeConfiguration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "timeRange",
            label: "Interval",
            valueType: "time-range",
            customDateMode: "range",
            comparators: [
                Comparators.GREATER_THAN_OR_EQUAL_TO,
                Comparators.LESS_THAN_OR_EQUAL_TO,
            ],
            dateFilterOptions: [
                {value: "START_DATE", label: "Start date"},
                {value: "END_DATE", label: "End date"},
                {value: "START_OR_END_DATE", label: "Start or end date"},
            ],
        },
    ],
}

describe("custom range decoding", () => {
    test("carries the route's dateFilter onto the merged range chip", () => {
        const query = {
            "filters[timeRange][GREATER_THAN_OR_EQUAL_TO]": "2026-08-01T00:00:00.000Z",
            "filters[timeRange][LESS_THAN_OR_EQUAL_TO]": "2026-08-02T00:00:00.000Z",
            [DATE_FILTER_KEY]: "END_DATE",
        }

        const {groups} = parseEncodedGroups(query, timeRangeConfiguration)
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        // The two bounds are merged into one chip by a pre-pass that used to drop the meta,
        // leaving the Apply-to selector on its first option instead of the one in the URL.
        expect(group.filters).toHaveLength(1)
        expect(group.filters[0]?.meta).toEqual({dateFilter: "END_DATE"})
    })

    test("leaves the merged range chip without meta when the route carries no dateFilter", () => {
        const query = {
            "filters[timeRange][GREATER_THAN_OR_EQUAL_TO]": "2026-08-01T00:00:00.000Z",
            "filters[timeRange][LESS_THAN_OR_EQUAL_TO]": "2026-08-02T00:00:00.000Z",
        }

        const {groups} = parseEncodedGroups(query, timeRangeConfiguration)
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        expect(group.filters[0]?.meta).toBeUndefined()
    })
})

const relativeRangeConfiguration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "timeRange",
            label: "Interval",
            valueType: "time-range",
            comparators: [Comparators.EQUALS],
        },
    ],
}

describe("relative date decoding", () => {
    test.each([
        ["P30D", "PT720H"],
        ["P1W", "PT168H"],
        ["P7D", "PT168H"],
        ["PT720H", "PT720H"],
    ])("normalizes the %s duration to %s", (value, expected) => {
        const {groups} = parseEncodedGroups({"filters[timeRange][EQUALS]": value}, relativeRangeConfiguration)
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        expect(group.filters[0]).toMatchObject({key: "timeRange", value: expected})
    })

    test("still reads an absolute date as a date rather than a duration", () => {
        const {groups} = parseEncodedGroups(
            {"filters[timeRange][EQUALS]": "2026-08-01T00:00:00.000Z"},
            relativeRangeConfiguration,
        )
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        expect(group.filters[0]?.value).toBeInstanceOf(Date)
    })
})

const dateRange = {
    startDate: new Date("2026-08-01T00:00:00.000Z"),
    endDate: new Date("2026-08-02T00:00:00.000Z"),
}

describe("range filter creation", () => {
    test("creates a time range chip", () => {
        const config: FilterKeyConfig = {
            key: "timeRange",
            label: "Interval",
            valueType: "time-range",
            comparators: [Comparators.EQUALS],
        }

        const filter = createTimeRangeFilter(config, dateRange.startDate, dateRange.endDate)

        expect(filter).toMatchObject({
            key: "timeRange",
            keyLabel: "Interval",
            comparator: Comparators.EQUALS,
            value: dateRange,
            valueLabel: `${dateRange.startDate.toLocaleDateString()} - ${dateRange.endDate.toLocaleDateString()}`,
        })
    })

    test("always keys the time range chip on the time range key", () => {
        const config: FilterKeyConfig = {
            key: "customKey",
            label: "Interval",
            valueType: "time-range",
            comparators: [Comparators.EQUALS],
        }

        expect(createTimeRangeFilter(config, dateRange.startDate, dateRange.endDate).key).toBe(TIME_RANGE_KEY)
    })

    test("creates a custom range chip with explicit bounds", () => {
        const config: FilterKeyConfig = {
            key: "createdAt",
            label: "Created at",
            valueType: "date",
            comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO],
        }

        const filter = createCustomRangeFilter(
            config.key,
            config,
            dateRange.startDate,
            dateRange.endDate,
        )

        expect(filter).toMatchObject({
            key: "createdAt",
            keyLabel: "Created at",
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            value: dateRange,
            valueLabel: `${dateRange.startDate.toLocaleDateString()} - ${dateRange.endDate.toLocaleDateString()}`,
        })
    })
})

describe("default visible filters", () => {
    const configKeys: FilterKeyConfig[] = [
        {
            key: "status",
            label: "Status",
            valueType: "select",
            comparators: [Comparators.EQUALS],
            visibleByDefault: true,
            defaultValue: "RUNNING",
        },
        {
            key: "labels",
            label: "Labels",
            valueType: "multi-select",
            comparators: [Comparators.IN],
            visibleByDefault: true,
            defaultValue: ["team:core"],
        },
        {
            key: "namespace",
            label: "Namespace",
            valueType: "text",
            comparators: [Comparators.EQUALS],
        },
    ]

    test("creates one chip per configured default in configuration order", () => {
        const filters = createDefaultVisibleFilters(configKeys, new Set(), new Set())

        expect(filters).toHaveLength(2)
        expect(filters).toMatchObject([
            {
                key: "status",
                comparator: Comparators.EQUALS,
                value: "RUNNING",
                valueLabel: "RUNNING",
                isDefaultVisible: true,
            },
            {
                key: "labels",
                comparator: Comparators.IN,
                value: ["team:core"],
                valueLabel: "team:core",
                isDefaultVisible: true,
            },
        ])
    })

    test("skips excluded and dismissed keys", () => {
        const filters = createDefaultVisibleFilters(configKeys, new Set(["status"]), new Set(["labels"]))

        expect(filters).toEqual([])
        expect(createDefaultVisibleFilters(configKeys, new Set(["status"]), new Set())).toMatchObject([
            {key: "labels"},
        ])
        expect(createDefaultVisibleFilters(configKeys, new Set(), new Set(["labels"]))).toMatchObject([
            {key: "status"},
        ])
    })

    test("falls back to the equals comparator when a default key has none", () => {
        const [filter] = createDefaultVisibleFilters(
            [{...configKeys[0], comparators: []}],
            new Set(),
            new Set(),
        )

        expect(filter.comparator).toBe(Comparators.EQUALS)
    })

    test("returns an empty list when no defaults are configured", () => {
        expect(createDefaultVisibleFilters(undefined, new Set(), new Set())).toEqual([])
    })

    test("resolves configured defaults and predictable fallbacks", () => {
        expect(resolveDefaultVisibleValue(configKeys[0])).toBe("RUNNING")
        expect(resolveDefaultVisibleValue({
            ...configKeys[0],
            defaultValue: () => "SUCCESS",
        })).toBe("SUCCESS")
        expect(resolveDefaultVisibleValue({
            ...configKeys[1],
            defaultValue: undefined,
        })).toEqual([])
        expect(resolveDefaultVisibleValue({
            ...configKeys[0],
            defaultValue: undefined,
        })).toBe("")
    })

    test("formats readable labels for every supported value kind", () => {
        expect(defaultVisibleValueLabel("RUNNING")).toBe("RUNNING")
        expect(defaultVisibleValueLabel(["team:core", "env:prod"])).toBe("team:core, env:prod")
        expect(defaultVisibleValueLabel(dateRange.startDate)).toBe(dateRange.startDate.toLocaleDateString())
        expect(defaultVisibleValueLabel(undefined as unknown as string)).toBe("")
        expect(defaultVisibleValueLabel(dateRange)).toBe(
            `${dateRange.startDate.toLocaleDateString()} - ${dateRange.endDate.toLocaleDateString()}`,
        )
    })
})

describe("createAppliedFilter", () => {
    const statusConfig: FilterKeyConfig = {
        key: "status",
        label: "Status",
        comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
    }

    test("produces a filter carrying the field, comparator and value it was given", () => {
        const filter = createAppliedFilter(
            "status",
            statusConfig,
            Comparators.EQUALS,
            "SUCCESS",
            "SUCCESS",
            "equals",
        )

        expect(filter).toMatchObject({
            key: "status",
            keyLabel: "Status",
            comparator: Comparators.EQUALS,
            comparatorLabel: "Equals",
            value: "SUCCESS",
            valueLabel: "SUCCESS",
        })
        expect(filter.id).toMatch(/^status-equals-\d+-[a-z0-9]+$/)
    })

    test("handles empty or undefined values without throwing", () => {
        expect(() =>
            createAppliedFilter("status", undefined, Comparators.EQUALS, "", "", "empty"),
        ).not.toThrow()

        const emptyFilter = createAppliedFilter("status", undefined, Comparators.EQUALS, "", "", "empty")
        expect(emptyFilter).toMatchObject({
            key: "status",
            value: "",
            valueLabel: "",
        })

        const undefFilter = createAppliedFilter("status", undefined, Comparators.EQUALS, undefined, "", "undef")
        expect(undefFilter).toMatchObject({
            key: "status",
            value: undefined,
            valueLabel: "",
        })
    })

    test("falls back to the raw key when config is undefined", () => {
        const filter = createAppliedFilter(
            "customField",
            undefined,
            Comparators.EQUALS,
            "val",
            "val",
            "test",
        )

        expect(filter.keyLabel).toBe("customField")
        expect(filter.comparatorLabel).toBe("Equals")
    })

    test("uses custom keyLabelProvider and comparatorLabels when provided", () => {
        const customConfig: FilterKeyConfig = {
            key: "labels",
            label: "Labels",
            comparators: [Comparators.EQUALS],
            comparatorLabels: {
                [Comparators.EQUALS]: "exactly equals",
            },
            keyLabelProvider: (meta) => `Label (${meta?.scope ?? "default"})`,
        }

        const filter = createAppliedFilter(
            "labels",
            customConfig,
            Comparators.EQUALS,
            "team:core",
            "team:core",
            "custom",
            {scope: "system"},
        )

        expect(filter.keyLabel).toBe("Label (system)")
        expect(filter.comparatorLabel).toBe("exactly equals")
        expect(filter.meta).toEqual({scope: "system"})
    })
})

describe("buildNewFilter", () => {
    test("seeds a blank filter with the documented defaults", () => {
        const config: FilterKeyConfig = {
            key: "state",
            label: "Execution state",
            comparators: [Comparators.IN, Comparators.NOT_IN],
        }

        const filter = buildNewFilter(config)

        expect(filter).not.toBeNull()
        expect(filter).toMatchObject({
            key: "state",
            keyLabel: "Execution state",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: [],
            valueLabel: "",
        })
        expect(filter?.id).toMatch(/^state-\d+-[a-z0-9]+$/)
    })

    test("returns null when the key config has no comparators", () => {
        const noComparatorsConfig: FilterKeyConfig = {
            key: "readonlyField",
            label: "Readonly",
            comparators: [],
        }

        expect(buildNewFilter(noComparatorsConfig)).toBeNull()
    })
})

describe("processFieldValue", () => {
    const textConfig: FilterKeyConfig = {
        key: "namespace",
        label: "Namespace",
        comparators: [Comparators.EQUALS, Comparators.CONTAINS],
    }

    const multiSelectConfig: FilterKeyConfig = {
        key: "state",
        label: "State",
        valueType: "multi-select",
        comparators: [Comparators.IN, Comparators.NOT_IN],
    }

    const keyValueConfig: FilterKeyConfig = {
        key: "labels",
        label: "Labels",
        valueType: "key-value",
        comparators: [Comparators.IN, Comparators.NOT_IN],
    }

    test("normalises a single value and an array of values the same way for scalar fields", () => {
        const fromScalar = processFieldValue(textConfig, [{value: "prod"}], Comparators.EQUALS)
        const fromArray = processFieldValue(textConfig, [{value: ["prod"]}], Comparators.EQUALS)

        expect(fromScalar).toEqual({value: "prod", valueLabel: "prod"})
        expect(fromArray).toEqual({value: "prod", valueLabel: "prod"})
    })

    test("normalises a single comma-separated string and an array the same way for multi-select fields", () => {
        const fromString = processFieldValue(multiSelectConfig, [{value: "SUCCESS,FAILED"}], Comparators.IN)
        const fromArray = processFieldValue(multiSelectConfig, [{value: ["SUCCESS", "FAILED"]}], Comparators.IN)

        expect(fromString).toEqual({
            value: ["SUCCESS", "FAILED"],
            valueLabel: "SUCCESS, FAILED",
        })
        expect(fromArray).toEqual({
            value: ["SUCCESS", "FAILED"],
            valueLabel: "SUCCESS, FAILED",
        })
    })

    test("normalises a single value and an array of values the same way for key-value fields", () => {
        const fromScalar = processFieldValue(keyValueConfig, [{value: "env:prod"}], Comparators.IN)
        const fromArray = processFieldValue(keyValueConfig, [{value: ["env:prod"]}], Comparators.IN)

        expect(fromScalar).toEqual({
            value: ["env:prod"],
            valueLabel: "env:prod",
        })
        expect(fromArray).toEqual({
            value: ["env:prod"],
            valueLabel: "env:prod",
        })
    })

    test("handles empty or undefined inputs without throwing", () => {
        expect(() => processFieldValue(textConfig, [], Comparators.EQUALS)).not.toThrow()
        expect(() => processFieldValue(multiSelectConfig, [], Comparators.IN)).not.toThrow()
        expect(() => processFieldValue(keyValueConfig, [], Comparators.IN)).not.toThrow()

        const emptyMulti = processFieldValue(multiSelectConfig, [], Comparators.IN)
        expect(emptyMulti).toEqual({value: [], valueLabel: ""})

        const emptyKV = processFieldValue(keyValueConfig, [], Comparators.IN)
        expect(emptyKV).toEqual({value: [], valueLabel: ""})

        const emptyScalar = processFieldValue(textConfig, [{value: ""}], Comparators.EQUALS)
        expect(emptyScalar).toEqual({value: "", valueLabel: ""})
    })

    test("normalizes date and relative duration values", () => {
        const dateConfig: FilterKeyConfig = {
            key: "created",
            label: "Created",
            valueType: "date",
            comparators: [Comparators.GREATER_THAN],
        }

        const dateResult = processFieldValue(
            dateConfig,
            [{value: "2026-08-01T00:00:00.000Z"}],
            Comparators.GREATER_THAN,
        )
        expect(dateResult.value).toBeInstanceOf(Date)

        const timeRangeConfig: FilterKeyConfig = {
            key: "timeRange",
            label: "Interval",
            valueType: "time-range",
            comparators: [Comparators.EQUALS],
        }

        const relativeResult = processFieldValue(
            timeRangeConfig,
            [{value: "P7D"}],
            Comparators.EQUALS,
        )
        expect(relativeResult).toEqual({value: "PT168H", valueLabel: "PT168H"})
    })
})

describe("isDateRangeValue", () => {
    test("is true for a range object with startDate and endDate", () => {
        const range = {
            startDate: new Date("2026-01-01T00:00:00Z"),
            endDate: new Date("2026-01-02T00:00:00Z"),
        }
        expect(isDateRangeValue(range)).toBe(true)
    })

    test.each<[string, AppliedFilter["value"]]>([
        ["an empty string", ""],
        ["a plain string", "2026-01-01"],
        ["a Date instance", new Date("2026-01-01T00:00:00Z")],
        ["an array", ["2026-01-01", "2026-01-02"]],
    ])("is false for %s", (_, value) => {
        expect(isDateRangeValue(value)).toBe(false)
    })
})
