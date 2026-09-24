import {describe, expect, it} from "vitest"
import {namespaceFileChips} from "./namespaceFileExpression"

describe("namespaceFileChips", () => {
    it("offers only read() for a text file", () => {
        const chips = namespaceFileChips("queries/idle_ec2.sql")
        expect(chips).toEqual([{label: "read('queries/idle_ec2.sql')", expr: "{{ read('queries/idle_ec2.sql') }}"}])
    })

    it("offers only fileURI() for a data file", () => {
        const chips = namespaceFileChips("fixtures/sample_response.json")
        expect(chips).toEqual([{label: "fileURI('fixtures/sample_response.json')", expr: "{{ fileURI('fixtures/sample_response.json') }}"}])
    })

    it("offers both expressions when the file type is ambiguous", () => {
        const chips = namespaceFileChips("notes.txt")
        expect(chips).toEqual([
            {label: "read('notes.txt')", expr: "{{ read('notes.txt') }}"},
            {label: "fileURI('notes.txt')", expr: "{{ fileURI('notes.txt') }}"},
        ])
    })

    it("offers both expressions for a file with no extension", () => {
        const chips = namespaceFileChips("Dockerfile")
        expect(chips.map(c => c.label)).toEqual(["read('Dockerfile')", "fileURI('Dockerfile')"])
    })

    it("strips a leading slash from the label", () => {
        const chips = namespaceFileChips("/queries/idle_ec2.sql")
        expect(chips[0].label).toEqual("read('queries/idle_ec2.sql')")
    })

    it("escapes a quote in the path so it cannot break out of the Pebble string literal", () => {
        const chips = namespaceFileChips("it's a file.sql")
        expect(chips[0].expr).toEqual("{{ read('it\\'s a file.sql') }}")
    })

    it("leaves a bare backslash untouched, since Pebble has no escape for it — verified against Pebble 4.1.2 to round-trip", () => {
        const chips = namespaceFileChips("dir\\legacy.sql")
        expect(chips[0].expr).toEqual("{{ read('dir\\legacy.sql') }}")
    })

    // A backslash immediately before a quote (or at the very end of the value) does not round-trip:
    // Pebble raises a ParserException rather than executing anything unintended, so this is a
    // documented, unreachable limitation (see escapePebbleLiteral) rather than an untested one.
    it("does not round-trip a backslash immediately before a quote — accepted as unreachable", () => {
        const chips = namespaceFileChips("dir\\'evil.sql")
        expect(chips[0].expr).toEqual("{{ read('dir\\\\'evil.sql') }}")
    })
})
