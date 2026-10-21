import {beforeEach, describe, expect, it} from "vitest"
import {setDocumentTitle} from "./documentTitle"

describe("setDocumentTitle", () => {
    beforeEach(() => {
        document.title = "Kestra"
    })

    it("prepends a page title to the base title", () => {
        setDocumentTitle("Flows")

        expect(document.title).toBe("Flows | Kestra")
    })

    it("uses the text after the last separator as the base title", () => {
        document.title = "Previous | Kestra"

        setDocumentTitle("Executions")

        expect(document.title).toBe("Executions | Kestra")
    })

    it.each([undefined, null, "", "   "])(
        "restores the bare base title for an empty page title (%j)",
        (title) => {
            document.title = "Previous | Kestra"

            setDocumentTitle(title)

            expect(document.title).toBe("Kestra")
        },
    )

    it("keeps the base title stable when the page title contains a separator", () => {
        setDocumentTitle("Parent | Child")
        setDocumentTitle("Runs")

        expect(document.title).toBe("Runs | Kestra")
    })
})
