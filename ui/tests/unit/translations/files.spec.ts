import {mkdtempSync, readFileSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {join} from "node:path"
import {beforeEach, describe, expect, it} from "vitest"
import {writeIfChanged} from "../../../scripts/translations/files.ts"

describe("writeIfChanged", () => {
    let file: string

    beforeEach(() => {
        file = join(mkdtempSync(join(tmpdir(), "kestra-translations-")), "out.json")
    })

    it("creates a file that does not exist yet", () => {
        expect(writeIfChanged(file, "{}")).toBe(true)
        expect(readFileSync(file, "utf-8")).toBe("{}")
    })

    it("does not rewrite a file whose content is unchanged", () => {
        writeFileSync(file, "{}")
        expect(writeIfChanged(file, "{}")).toBe(false)
    })

    // The generator serialises with JSON.stringify, which emits no final newline, while editors add
    // one. Without this, every scheduled run stripped those newlines back off and opened a PR whose
    // entire diff was "\ No newline at end of file" — kestra-io/kestra#17823.
    it("treats a trailing-newline-only difference as no change", () => {
        writeFileSync(file, "{}\n")
        expect(writeIfChanged(file, "{}")).toBe(false)
        expect(readFileSync(file, "utf-8")).toBe("{}\n")
    })

    it("keeps the existing trailing newline when the content really changes", () => {
        writeFileSync(file, "{\"a\": 1}\n")
        expect(writeIfChanged(file, "{\"a\": 2}")).toBe(true)
        expect(readFileSync(file, "utf-8")).toBe("{\"a\": 2}\n")
    })

    it("keeps the absence of a trailing newline when the content really changes", () => {
        writeFileSync(file, "{\"a\": 1}")
        expect(writeIfChanged(file, "{\"a\": 2}\n")).toBe(true)
        expect(readFileSync(file, "utf-8")).toBe("{\"a\": 2}")
    })
})
