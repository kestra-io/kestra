import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const BASE_URL = "http://localhost/api/v1/main"

const mockServer = {
    id: "my-server",
    serverType: "PRIVATE" as const,
    authType: "BASIC" as const,
    disabled: false,
    isDefault: false,
}

const axiosGet = vi.fn().mockResolvedValue({data: {results: [mockServer], total: 1}})
const axiosPost = vi.fn().mockResolvedValue({data: mockServer})
const axiosPut = vi.fn().mockResolvedValue({data: mockServer})
const axiosDelete = vi.fn().mockResolvedValue({data: undefined})
const axiosPatch = vi.fn().mockResolvedValue({data: {...mockServer, disabled: true}})

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: axiosGet,
        post: axiosPost,
        put: axiosPut,
        delete: axiosDelete,
        patch: axiosPatch,
    }),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => BASE_URL,
}))

describe("mcp store", () => {
    beforeEach(() => {
        vi.resetModules()
        axiosGet.mockClear()
        axiosPost.mockClear()
        axiosPut.mockClear()
        axiosDelete.mockClear()
        axiosPatch.mockClear()
        setActivePinia(createPinia())
        localStorage.clear()
    })

    it("list() calls GET /mcp/servers and returns data", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const result = await store.list()

        expect(axiosGet).toHaveBeenCalledOnce()
        expect(axiosGet).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers`)
        expect(result).toEqual({results: [mockServer], total: 1})
    })

    it("load() calls GET /mcp/servers/{id} and sets server", async () => {
        axiosGet.mockResolvedValueOnce({data: mockServer})
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        await store.load("my-server")

        expect(axiosGet).toHaveBeenCalledOnce()
        expect(axiosGet).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server`)
        expect(store.server).toEqual(mockServer)
    })

    it("load() sets server to null when server is not found", async () => {
        axiosGet.mockRejectedValueOnce(new Error("404"))
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        await store.load("nonexistent")

        expect(store.server).toBeNull()
    })

    it("create() calls POST /mcp/servers with payload and returns server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const payload = {id: "my-server", serverType: "PRIVATE" as const, authType: "BASIC" as const, disabled: false}
        const result = await store.create(payload)

        expect(axiosPost).toHaveBeenCalledOnce()
        expect(axiosPost).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers`, payload)
        expect(result).toEqual(mockServer)
    })

    it("update() calls PUT /mcp/servers/{id} with payload and returns server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const payload = {id: "my-server", serverType: "PUBLIC" as const, authType: "BASIC" as const, disabled: false}
        const result = await store.update("my-server", payload)

        expect(axiosPut).toHaveBeenCalledOnce()
        expect(axiosPut).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server`, payload)
        expect(result).toEqual(mockServer)
    })

    it("remove() calls DELETE /mcp/servers/{id}", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        await store.remove("my-server")

        expect(axiosDelete).toHaveBeenCalledOnce()
        expect(axiosDelete).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server`)
    })

    it("toggle() calls PATCH /mcp/servers/{id}/toggle and returns updated server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const result = await store.toggle("my-server")

        expect(axiosPatch).toHaveBeenCalledOnce()
        expect(axiosPatch).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server/toggle`)
        expect(result).toEqual({...mockServer, disabled: true})
    })
})
