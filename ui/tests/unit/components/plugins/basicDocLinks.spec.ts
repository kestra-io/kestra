import {describe, expect, test} from "vitest"
import basicMd from "../../../../src/assets/docs/basic.md?raw"

// PluginDocumentation renders one "Learn more" card per link in this section, labelled
// with the markdown link text - duplicate labels make the cards indistinguishable.
describe("basic.md - Links to learn more", () => {
    const section = basicMd.split("### Links to learn more")[1]?.split(/\n#{1,3} /)[0] ?? ""
    const labels = [...section.matchAll(/\[([^\]]+)\]\(/g)].map((match) => match[1])

    test("the section is found and contains links", () => {
        expect(labels.length).toBeGreaterThan(0)
    })

    test("every link label is unique", () => {
        expect(labels).toStrictEqual([...new Set(labels)])
    })
})