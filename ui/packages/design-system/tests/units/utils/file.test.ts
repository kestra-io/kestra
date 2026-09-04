import {describe, test, expect} from "vitest"
import {fileExtension, fileIcon, fileName, isFileUri} from "../../../src/utils/file"

describe("isFileUri", () => {
    test("accepts the internal storage schemes", () => {
        expect(isFileUri("kestra:///company/flow/outputs/abc.txt")).toBe(true)
        expect(isFileUri("file:///tmp/abc.txt")).toBe(true)
        expect(isFileUri("nsfile:///scripts/main.py")).toBe(true)
    })

    test("rejects anything that is not one of them", () => {
        expect(isFileUri("https://kestra.io/logo.png")).toBe(false)
        expect(isFileUri("abc.txt")).toBe(false)
        expect(isFileUri(42)).toBe(false)
        expect(isFileUri(null)).toBe(false)
    })
})

describe("fileName", () => {
    test("returns the last segment, decoded and without query or fragment", () => {
        expect(fileName("kestra:///company/outputs/my%20report.csv?v=2#top")).toBe("my report.csv")
    })

    test("ignores a trailing slash rather than returning an empty name", () => {
        expect(fileName("kestra:///company/outputs/")).toBe("outputs")
    })
})

describe("fileExtension", () => {
    test("is lowercased and taken from the last dot", () => {
        expect(fileExtension("kestra:///out/archive.tar.GZ")).toBe("gz")
    })

    test("is empty for a name without one, and for a dotfile", () => {
        expect(fileExtension("kestra:///out/abc")).toBe("")
        expect(fileExtension("kestra:///out/.gitignore")).toBe("")
    })
})

describe("fileIcon", () => {
    test("maps an extension to its family, and the rest to the generic file", () => {
        expect(fileIcon("kestra:///out/data.csv")).not.toBe(fileIcon("kestra:///out/data.png"))
        expect(fileIcon("kestra:///out/abc")).toBe(fileIcon("kestra:///out/data.unknownext"))
    })

    test("does not resolve an extension that names an Object.prototype member", () => {
        const generic = fileIcon("kestra:///out/abc")
        expect(fileIcon("kestra:///out/report.constructor")).toBe(generic)
        expect(fileIcon("kestra:///out/report.__proto__")).toBe(generic)
    })

    test("resolves a bare file name too, not only a URI", () => {
        expect(fileIcon("report.csv")).toBe(fileIcon("kestra:///out/report.csv"))
    })
})
