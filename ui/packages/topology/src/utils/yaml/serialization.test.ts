import {describe, expect, test} from "vitest"
import * as YamlUtils from "./serialization.ts"

describe("parse", () => {
    test.each(["", "  \n", "# only a comment\n"])("returns undefined for a stream with no document: %j", (source) => {
        expect(YamlUtils.parse(source)).toBeUndefined()
    })

    test("still rejects a stream holding several documents", () => {
        expect(() => YamlUtils.parse("a: 1\n---\nb: 2\n")).toThrow()
    })

    test("resolves a merge key", () => {
        expect(YamlUtils.parse("base: &base {a: 1}\nx:\n  <<: *base\n  b: 2\n")).toEqual({base: {a: 1}, x: {a: 1, b: 2}})
    })

    test("keeps an unquoted date as a string", () => {
        expect(YamlUtils.parse("at: 2024-01-01\n")).toEqual({at: "2024-01-01"})
    })
})

describe("stringify", () => {
    test.each(["yes", "on", "no", "off", "y"])("quotes the YAML 1.1 boolean word %j so it stays a string", (word) => {
        expect(YamlUtils.stringify({value: word})).toBe(`value: "${word}"\n`)
    })

    test("writes a Date as a timestamp", () => {
        expect(YamlUtils.stringify({at: new Date("2024-01-01T00:00:00.000Z")})).toBe("at: 2024-01-01T00:00:00.000Z\n")
    })
})

describe("stringify with preserveCronQuotes", () => {
    test("adds quotes to unquoted cron values", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * *\"")
    })

    test("preserves double quotes in cron values", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "\"0 0 * * *\"",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // When input already has quotes, preserveCronQuotes should skip adding quotes
        expect(result).toContain("cron:")
        expect(result).toContain("0 0 * * *")
        expect(result).toMatch(/cron:\s*"\\"0 0 \* \* \*\\""|cron:\s*"0 0 \* \* \*"/)
    })

    test("preserves single quotes in cron values", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "'0 0 * * *'",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron:")
        expect(result).toContain("'0 0 * * *'")
    })

    test("does not quote multiline strings starting with pipe", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "|\n  0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: |")
        expect(result).not.toContain("cron: \"|")
    })

    test("does not quote multiline strings starting with greater than", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: ">\n  0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron:")
        expect(result).not.toContain("cron: \">")
        expect(result).toMatch(/cron:\s*[|>]/)
    })

    test("handles cron values with comments", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * * # daily",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * * # daily\"")
    })

    test("handles multiple cron triggers", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                },
                {
                    id: "trigger2",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 12 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * *\"")
        expect(result).toContain("cron: \"0 12 * * *\"")
    })

    test("handles cron with indentation", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toMatch(/\n {2}- id: trigger1\n {4}type: .*?\n {4}cron: "0 0 \* \* \*"/)
    })

    test("handles empty cron value without adding quotes", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).not.toContain("cron: \"\"")
        expect(result).toMatch(/cron:\s*$/m)
    })

    test("handles cron with whitespace-only value", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "   ",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"   \"")
    })

    test("does not affect non-cron fields", () => {
        const yaml = {
            id: "test-flow",
            namespace: "io.kestra.test",
            tasks: [
                {
                    id: "task1",
                    type: "io.kestra.plugin.core.log.Log",
                    message: "0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("message:")
        expect(result).toContain("0 0 * * *")
        expect(result).not.toMatch(/message:\s*"0 0 \* \* \*"/)
    })

    test("handles cron in nested structures", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                    conditions: [
                        {
                            id: "condition1",
                            type: "io.kestra.plugin.core.condition.Expression",
                            expression: "{{ true }}",
                        },
                    ],
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * *\"")
    })
})

describe("sortPredicate", () => {
    const sortKeys = (keys: string[]) => [...keys].sort(YamlUtils.sortPredicate)

    test("puts known keys in their documented order, id and type first", () => {
        const documentedOrder = [
            "id",
            "type",
            "namespace",
            "description",
            "revision",
            "inputs",
            "variables",
            "tasks",
            "errors",
            "triggers",
            "listeners",
            "pluginDefaults",
        ]

        expect(sortKeys([...documentedOrder].reverse())).toEqual(documentedOrder)
    })

    test("sorts an unknown key after the known ones without dropping it", () => {
        expect(sortKeys(["tasks", "customKey", "id"])).toEqual(["id", "tasks", "customKey"])
    })

    test("keeps a deterministic relative order for two unknown keys", () => {
        expect(YamlUtils.sortPredicate("zeta", "alpha")).toBe(0)
        expect(sortKeys(["zeta", "alpha"])).toEqual(["zeta", "alpha"])
        expect(sortKeys(["alpha", "zeta"])).toEqual(["alpha", "zeta"])
        expect(sortKeys(["zeta", "id", "alpha"])).toEqual(["id", "zeta", "alpha"])
    })

    test("is symmetric and sorts the same keys twice to the same result", () => {
        const keys = ["triggers", "customB", "id", "namespace", "customA", "type"]
        const pairs = [
            ["id", "type"],
            ["triggers", "id"],
            ["id", "customA"],
            ["customB", "customA"],
            ["tasks", "tasks"],
        ]

        for (const [left, right] of pairs) {
            expect(YamlUtils.sortPredicate(left, right) === -YamlUtils.sortPredicate(right, left)).toBe(true)
        }

        const once = sortKeys(keys)
        expect(sortKeys(once)).toEqual(once)
    })

    test("sorts an empty key list without throwing", () => {
        expect(() => sortKeys([])).not.toThrow()
        expect(sortKeys([])).toEqual([])
    })
})
