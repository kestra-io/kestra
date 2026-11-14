import {describe, test, expect} from "vitest"
import {processLinkTags, linkTagsAsText} from "./linkify"

const marker = "[[link execution=\"exec1\" flowId=\"flow1\" namespace=\"ns1\"]]"
const routerTag = "<router-md execution=\"exec1\" flowId=\"flow1\" namespace=\"ns1\"></router-md>"

const incompleteMarkers = [
    ["execution", "[[link flowId=\"flow1\" namespace=\"ns1\"]]"],
    ["flowId", "[[link execution=\"exec1\" namespace=\"ns1\"]]"],
    ["namespace", "[[link execution=\"exec1\" flowId=\"flow1\"]]"],
]

describe("processLinkTags", () => {
    test("turns a complete marker into a router-md tag carrying all three attributes", () => {
        expect(processLinkTags(marker)).toBe(routerTag)
    })

    test("replaces every marker in a message", () => {
        const other = "[[link execution=\"exec2\" flowId=\"flow2\" namespace=\"ns2\"]]"

        expect(processLinkTags(`${marker} then ${other}`)).toBe(
            `${routerTag} then <router-md execution="exec2" flowId="flow2" namespace="ns2"></router-md>`,
        )
    })

    test.each(incompleteMarkers)("returns a marker missing %s unchanged", (_missing, incomplete) => {
        expect(processLinkTags(incomplete)).toBe(incomplete)
    })

    test("preserves the text around a marker", () => {
        expect(processLinkTags(`Started ${marker} successfully`)).toBe(`Started ${routerTag} successfully`)
    })

    test("returns a message without a marker as is", () => {
        expect(processLinkTags("Nothing to link here")).toBe("Nothing to link here")
    })

    test("reads attributes given in a different order", () => {
        expect(processLinkTags("[[link namespace=\"ns1\" execution=\"exec1\" flowId=\"flow1\"]]")).toBe(routerTag)
    })
})

describe("linkTagsAsText", () => {
    test("replaces a complete marker with the execution id alone", () => {
        expect(linkTagsAsText(`Started ${marker} successfully`)).toBe("Started exec1 successfully")
    })

    test.each(incompleteMarkers)("leaves a marker missing %s unchanged", (_missing, incomplete) => {
        expect(linkTagsAsText(incomplete)).toBe(incomplete)
    })

    test("reads attributes given in a different order", () => {
        expect(linkTagsAsText("[[link flowId=\"flow1\" namespace=\"ns1\" execution=\"exec1\"]]")).toBe("exec1")
    })
})