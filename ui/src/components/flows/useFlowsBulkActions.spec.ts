import {afterAll, beforeAll, beforeEach, describe, expect, test, vi} from "vitest"
import {ref} from "vue"
import {configureClient} from "@kestra-io/kestra-sdk"
import useFlowsBulkActions from "./useFlowsBulkActions"

const downloads: Array<{blob: Blob, filename: string}> = []
const {error} = vi.hoisted(() => ({error: vi.fn()}))

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key, te: () => false})}))
vi.mock("../../utils/toast", () => ({
    useToast: () => ({
        confirm: (_message: string, callback: () => Promise<unknown>) => callback(),
        success: vi.fn(),
        error,
    }),
}))
vi.mock("../../stores/flow", () => ({useFlowStore: () => ({total: 2})}))
vi.mock("../../utils/utils", () => ({
    downloadBlob: (blob: Blob, filename: string) => downloads.push({blob, filename}),
}))

const ZIP_BYTES = Uint8Array.from([0x50, 0x4b, 0x03, 0x04, ...Array.from({length: 256}, (_, i) => i)])

const zipFetch = vi.fn(async (..._args: unknown[]) => new Response(ZIP_BYTES, {
    status: 200,
    headers: {"Content-Type": "application/octet-stream", "Content-Disposition": "attachment; filename=\"flows.zip\""},
}))

function readBytes(blob: Blob): Promise<Uint8Array> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer))
        reader.onerror = () => reject(reader.error)
        reader.readAsArrayBuffer(blob)
    })
}

function mountBulkActions(queryBulkAction: boolean) {
    return useFlowsBulkActions({
        loadQuery: () => ({}),
        file: ref(null),
        dataTable: ref({
            selection: [{id: "export_a", namespace: "qa.followup", enabled: true}],
            queryBulkAction,
            toggleAllUnselected: vi.fn(),
            reload: vi.fn(),
        }),
    })
}

describe("useFlowsBulkActions exportFlows", () => {
    beforeEach(() => {
        downloads.length = 0
    })

    beforeAll(() => {
        configureClient({baseUrl: "http://localhost", fetch: zipFetch})
    })

    afterAll(() => {
        configureClient({fetch: undefined})
    })

    test.each([
        ["selected flows", false],
        ["every flow matching the query", true],
    ])("downloads the ZIP bytes untouched when exporting %s", async (_label, queryBulkAction) => {
        // Given
        const {exportFlows} = mountBulkActions(queryBulkAction)

        // When
        exportFlows()
        await vi.waitFor(() => expect(downloads).toHaveLength(1))

        // Then
        expect(downloads.map((download) => download.filename)).toEqual(["flows.zip"])
        expect(Array.from(await readBytes(downloads[0].blob))).toEqual(Array.from(ZIP_BYTES))
    })
})

describe("useFlowsBulkActions deleteFlows", () => {
    const refused = {
        type: "https://kestra.io/problems/bulk-validation-failed",
        title: "Bulk validation failed",
        status: 400,
        detail: "One or more flows could not be deleted.",
        errors: [{detail: "flow not found", path: "qa.followup.export_a"}],
    }

    beforeAll(() => {
        configureClient({
            baseUrl: "http://localhost",
            fetch: async () => new Response(JSON.stringify(refused), {status: 400, headers: {"Content-Type": "application/problem+json"}}),
        })
    })

    afterAll(() => {
        configureClient({fetch: undefined})
    })

    beforeEach(() => {
        vi.clearAllMocks()
    })

    test("lists the refused flows when the server refuses a bulk delete", async () => {
        const {deleteFlows} = mountBulkActions(false)

        deleteFlows()

        await vi.waitFor(() => expect(error).toHaveBeenCalledWith([{message: "flow not found"}], "Bulk validation failed"))
    })
})
