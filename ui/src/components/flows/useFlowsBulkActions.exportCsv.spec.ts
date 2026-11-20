import {afterAll, beforeAll, beforeEach, describe, expect, test, vi} from "vitest"
import {ref} from "vue"
import {configureClient} from "@kestra-io/kestra-sdk"
import useFlowsBulkActions from "./useFlowsBulkActions"

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))
vi.mock("../../utils/toast", () => ({useToast: () => ({confirm: vi.fn(), success: vi.fn()})}))
vi.mock("../../stores/flow", () => ({useFlowStore: () => ({total: 0})}))

const CSV = "id,namespace\nexport_a,qa.followup\nexport_b,qa.followup\n"

function requestUrl(input: URL | RequestInfo): URL {
    return new URL(input instanceof Request ? input.url : input)
}

const backendFetch = vi.fn(async (input: URL | RequestInfo, _init?: RequestInit) => {
    const {pathname} = requestUrl(input)
    if (pathname.endsWith("/flows/export/by-query/csv")) {
        return new Response(CSV, {status: 200, headers: {"Content-Type": "text/csv"}})
    }
    return new Response(JSON.stringify({title: "Not Acceptable", status: 406}), {
        status: 406,
        headers: {"Content-Type": "application/problem+json"},
    })
})

function readText(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.onerror = () => reject(reader.error)
        reader.readAsText(blob)
    })
}

describe("useFlowsBulkActions exportFlowsAsStream", () => {
    const createdBlobs: Blob[] = []
    const clickedDownloads: string[] = []

    beforeAll(() => {
        configureClient({baseUrl: "http://localhost", fetch: backendFetch})
    })

    afterAll(() => {
        configureClient({fetch: undefined})
    })

    beforeEach(() => {
        createdBlobs.length = 0
        clickedDownloads.length = 0
        backendFetch.mockClear()
        URL.createObjectURL = (blob: Blob | MediaSource) => {
            createdBlobs.push(blob as Blob)
            return "blob:flows-csv"
        }
        URL.revokeObjectURL = vi.fn()
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            clickedDownloads.push(this.getAttribute("download") ?? "")
        })
    })

    test("shouldDownloadTheCsvFromTheCsvEndpointWhenExportingAsCsv", async () => {
        // Given
        const {exportFlowsAsStream} = useFlowsBulkActions({
            loadQuery: () => ({}),
            file: ref(null),
            dataTable: ref(null),
        })

        // When
        await exportFlowsAsStream({})

        // Then
        expect(clickedDownloads).toEqual(["flows.csv"])
        expect(await readText(createdBlobs[0])).toEqual(CSV)
        expect(requestUrl(backendFetch.mock.calls[0][0]).pathname).toMatch(/\/flows\/export\/by-query\/csv$/)
    })
})
