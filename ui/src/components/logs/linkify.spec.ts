import {describe, test, expect} from "vitest"
import {processLinkTags, linkTagsAsText} from "./linkify"

const marker = (execution: string, flowId: string, namespace: string) =>
    `[[link execution="${execution}" flowId="${flowId}" namespace="${namespace}"]]`

const routerTag = (execution: string, flowId: string, namespace: string) =>
    `<router-md execution="${execution}" flowId="${flowId}" namespace="${namespace}"></router-md>`

describe("processLinkTags", () => {
    test("turns a complete marker into a router-md tag carrying all three attributes", () => {
        expect(processLinkTags(marker("exec1", "my-flow", "company.team"))).toBe(
            routerTag("exec1", "my-flow", "company.team"),
        )
    })

    test("replaces every marker in a message", () => {
        const message = `${marker("exec1", "flow-a", "ns.a")} then ${marker("exec2", "flow-b", "ns.b")}`
        expect(processLinkTags(message)).toBe(
            `${routerTag("exec1", "flow-a", "ns.a")} then ${routerTag("exec2", "flow-b", "ns.b")}`,
        )
    })

    test.each([
        ["execution", "[[link flowId=\"my-flow\" namespace=\"company.team\"]]"],
        ["flowId", "[[link execution=\"exec1\" namespace=\"company.team\"]]"],
        ["namespace", "[[link execution=\"exec1\" flowId=\"my-flow\"]]"],
    ])("returns a marker missing %s unchanged", (_missing, incomplete) => {
        expect(processLinkTags(incomplete)).toBe(incomplete)
    })

    test("returns a marker with an empty attribute unchanged", () => {
        const incomplete = "[[link execution=\"\" flowId=\"my-flow\" namespace=\"company.team\"]]"
        expect(processLinkTags(incomplete)).toBe(incomplete)
    })

    test("replaces a complete marker and leaves an incomplete one in the same message", () => {
        const incomplete = "[[link execution=\"exec2\" flowId=\"flow-b\"]]"
        expect(processLinkTags(`${marker("exec1", "flow-a", "ns.a")} ${incomplete}`)).toBe(
            `${routerTag("exec1", "flow-a", "ns.a")} ${incomplete}`,
        )
    })

    test("keeps the text around a marker", () => {
        expect(processLinkTags(`Started ${marker("exec1", "my-flow", "company.team")} successfully`)).toBe(
            `Started ${routerTag("exec1", "my-flow", "company.team")} successfully`,
        )
    })

    test("returns a message with no marker as is", () => {
        const message = "Task finished in 3s [not a link] [[other thing]]"
        expect(processLinkTags(message)).toBe(message)
        expect(processLinkTags("")).toBe("")
    })

    test("reads attributes written in a different order", () => {
        expect(
            processLinkTags("[[link namespace=\"company.team\" execution=\"exec1\" flowId=\"my-flow\"]]"),
        ).toBe(routerTag("exec1", "my-flow", "company.team"))
    })
})

describe("linkTagsAsText", () => {
    test("replaces a complete marker with the execution id alone", () => {
        expect(linkTagsAsText(marker("exec1", "my-flow", "company.team"))).toBe("exec1")
    })

    test("keeps the text around the marker", () => {
        expect(linkTagsAsText(`Started ${marker("exec1", "my-flow", "company.team")} successfully`)).toBe(
            "Started exec1 successfully",
        )
    })

    test("replaces every marker in a message", () => {
        const message = `${marker("exec1", "flow-a", "ns.a")} then ${marker("exec2", "flow-b", "ns.b")}`
        expect(linkTagsAsText(message)).toBe("exec1 then exec2")
    })

    test.each([
        ["execution", "[[link flowId=\"my-flow\" namespace=\"company.team\"]]"],
        ["flowId", "[[link execution=\"exec1\" namespace=\"company.team\"]]"],
        ["namespace", "[[link execution=\"exec1\" flowId=\"my-flow\"]]"],
    ])("leaves a marker missing %s unchanged", (_missing, incomplete) => {
        expect(linkTagsAsText(incomplete)).toBe(incomplete)
    })

    test("reads attributes written in a different order", () => {
        expect(
            linkTagsAsText("[[link flowId=\"my-flow\" namespace=\"company.team\" execution=\"exec1\"]]"),
        ).toBe("exec1")
    })

    test("returns a message with no marker as is", () => {
        const message = "Task finished in 3s"
        expect(linkTagsAsText(message)).toBe(message)
    })
})
