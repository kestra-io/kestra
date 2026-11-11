import {afterAll, beforeAll, beforeEach, describe, expect, test, vi} from "vitest"
import {ref} from "vue"
import {configureClient} from "@kestra-io/kestra-sdk"
import useFlowsBulkActions from "./useFlowsBulkActions"

const downloads: Array<{url: string, filename: string}> = []

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))
vi.mock("../../utils/toast", () => ({
    useToast: () => ({
        confirm: (_message: string, callback: () => Promise<unknown>) => callback(),
        success: vi.fn(),
    }),
}))
vi.mock("../../stores/flow", () => ({useFlowStore: () => ({total: 2})}))
vi.mock("../../utils/utils", () => ({
    downloadUrl: (url: string, filename: string) => downloads.push({url, filename}),
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
    const createdBlobs: Blob[] = []

    beforeEach(() => {
        downloads.length = 0
        createdBlobs.length = 0
        URL.createObjectURL = (blob: Blob | MediaSource) => {
            createdBlobs.push(blob as Blob)
            return "blob:flows"
        }
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
        expect(downloads).toEqual([{url: "blob:flows", filename: "flows.zip"}])
        expect(Array.from(await readBytes(createdBlobs[0]))).toEqual(Array.from(ZIP_BYTES))
    })
})
