import {describe, expect, it} from "vitest"
import {capitalize, formatPluginTitle, getShortName, hashCode} from "./global"

describe("global string helpers", () => {
    describe("capitalize", () => {
        it("upper-cases the first character and leaves the rest alone", () => {
            expect(capitalize("hello")).toBe("Hello")
            expect(capitalize("hello world")).toBe("Hello world")
            expect(capitalize("fooBar")).toBe("FooBar")
            expect(capitalize("camelCase")).toBe("CamelCase")
        })

        it("handles already capitalized strings", () => {
            expect(capitalize("Hello")).toBe("Hello")
            expect(capitalize("WORLD")).toBe("WORLD")
        })

        it("handles single-character strings", () => {
            expect(capitalize("a")).toBe("A")
            expect(capitalize("Z")).toBe("Z")
        })

        it("handles empty string", () => {
            expect(capitalize("")).toBe("")
        })

        it("handles strings starting with non-alphabetical characters", () => {
            expect(capitalize("123abc")).toBe("123abc")
            expect(capitalize("!important")).toBe("!important")
            expect(capitalize(" hello")).toBe(" hello")
        })

        it("supports String.prototype.capitalize extension", () => {
            expect("hello".capitalize()).toBe("Hello")
            expect("".capitalize()).toBe("")
            expect("fooBar".capitalize()).toBe("FooBar")
        })
    })

    describe("getShortName", () => {
        it("extracts the segment after the last dot", () => {
            expect(getShortName("io.kestra.plugin.core.log.Log")).toBe("Log")
            expect(getShortName("io.kestra.core.models.flows.Flow")).toBe("Flow")
            expect(getShortName("io.kestra.plugin.fs.ssh.Download")).toBe("Download")
            expect(getShortName("org.example.CustomTask")).toBe("CustomTask")
            expect(getShortName("a.b")).toBe("b")
        })

        it("returns the full string if there are no dots", () => {
            expect(getShortName("Log")).toBe("Log")
            expect(getShortName("standalone")).toBe("standalone")
            expect(getShortName("")).toBe("")
        })

        it("handles leading, trailing, and consecutive dots", () => {
            expect(getShortName(".hidden")).toBe("hidden")
            expect(getShortName("trailing.")).toBe("")
            expect(getShortName("a..b")).toBe("b")
        })
    })

    describe("formatPluginTitle", () => {
        it("returns undefined for undefined and empty string", () => {
            expect(formatPluginTitle(undefined)).toBeUndefined()
            expect(formatPluginTitle("")).toBeUndefined()
        })

        it("leaves all-caps untouched when length is greater than 1", () => {
            expect(formatPluginTitle("AWS")).toBe("AWS")
            expect(formatPluginTitle("GCP")).toBe("GCP")
            expect(formatPluginTitle("SQL")).toBe("SQL")
            expect(formatPluginTitle("HTTP")).toBe("HTTP")
            expect(formatPluginTitle("SFTP")).toBe("SFTP")
        })

        it("capitalizes lowercase strings", () => {
            expect(formatPluginTitle("python")).toBe("Python")
            expect(formatPluginTitle("docker")).toBe("Docker")
            expect(formatPluginTitle("git")).toBe("Git")
            expect(formatPluginTitle("bash")).toBe("Bash")
        })

        it("capitalizes single letters", () => {
            expect(formatPluginTitle("a")).toBe("A")
            expect(formatPluginTitle("A")).toBe("A")
        })

        it("preserves already capitalized mixed-case strings", () => {
            expect(formatPluginTitle("Kafka")).toBe("Kafka")
            expect(formatPluginTitle("Postgres")).toBe("Postgres")
        })
    })

    describe("hashCode", () => {
        it("returns 0 for empty string", () => {
            expect(hashCode("")).toBe(0)
        })

        it("produces stable hashes for the same string", () => {
            expect(hashCode("hello")).toBe(hashCode("hello"))
            expect(hashCode("kestra")).toBe(hashCode("kestra"))
            expect(hashCode("a")).toBe(97)
            expect(hashCode("hello")).toBe(99162322)
        })

        it("produces distinct hashes for different strings", () => {
            expect(hashCode("apple")).not.toBe(hashCode("banana"))
            expect(hashCode("kestra")).not.toBe(hashCode("workflow"))
            expect(hashCode("test1")).not.toBe(hashCode("test2"))
        })

        it("returns a 32-bit signed integer", () => {
            const hash = hashCode("io.kestra.plugin.core.log.Log")
            expect(hash).toBe(-1875787894)
            expect(hash).toBeGreaterThanOrEqual(-2147483648)
            expect(hash).toBeLessThanOrEqual(2147483647)
        })

        it("supports String.prototype.hashCode extension", () => {
            expect("hello".hashCode()).toBe(hashCode("hello"))
            expect("".hashCode()).toBe(0)
            expect("kestra".hashCode()).toBe(hashCode("kestra"))
        })
    })
})
