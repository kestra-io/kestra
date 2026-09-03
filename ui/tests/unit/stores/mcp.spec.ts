import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const mockServer = {
    id: "my-server",
    serverType: "PRIVATE" as const,
    authType: "BASIC" as const,
    disabled: false,
    isDefault: false,
}

const listMcps = vi.fn().mockResolvedValue({results: [mockServer], total: 1})
const mcp = vi.fn().mockResolvedValue(mockServer)
const createMcp = vi.fn().mockResolvedValue(mockServer)
const updateMcp = vi.fn().mockResolvedValue(mockServer)
const deleteMcp = vi.fn().mockResolvedValue(undefined)
const toggleMcp = vi.fn().mockResolvedValue({...mockServer, disabled: true})
const listTools = vi.fn().mockResolvedValue([])

vi.mock("@kestra-io/kestra-sdk/mcp", () => ({
    listMcps: (...args: any[]) => listMcps(...args),
    mcp: (...args: any[]) => mcp(...args),
    createMcp: (...args: any[]) => createMcp(...args),
    updateMcp: (...args: any[]) => updateMcp(...args),
    deleteMcp: (...args: any[]) => deleteMcp(...args),
    toggleMcp: (...args: any[]) => toggleMcp(...args),
    listTools: (...args: any[]) => listTools(...args),
}))

describe("mcp store", () => {
    beforeEach(() => {
        vi.resetModules()
        listMcps.mockClear()
        mcp.mockClear()
        createMcp.mockClear()
        updateMcp.mockClear()
        deleteMcp.mockClear()
        toggleMcp.mockClear()
        listTools.mockClear()
        setActivePinia(createPinia())
        localStorage.clear()
    })

    it("list() calls listMcps and returns data", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const result = await store.list()

        expect(listMcps).toHaveBeenCalledOnce()
        expect(result).toEqual({results: [mockServer], total: 1})
    })

    it("load() calls mcp() and sets server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        await store.load("my-server")

        expect(mcp).toHaveBeenCalledOnce()
        expect(mcp).toHaveBeenCalledWith({id: "my-server"})
        expect(store.server).toEqual(mockServer)
    })

    it("load() sets server to null when server is not found", async () => {
        mcp.mockRejectedValueOnce(new Error("404"))
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        await store.load("nonexistent")

        expect(store.server).toBeNull()
    })

    it("create() calls createMcp with payload and returns server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const payload = {id: "my-server", serverType: "PRIVATE" as const, authType: "BASIC" as const, disabled: false}
        const result = await store.create(payload)

        expect(createMcp).toHaveBeenCalledOnce()
        expect(createMcp).toHaveBeenCalledWith(payload)
        expect(result).toEqual(mockServer)
    })

    it("update() calls updateMcp with id and payload and returns server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const payload = {id: "my-server", serverType: "PUBLIC" as const, authType: "BASIC" as const, disabled: false}
        const result = await store.update("my-server", payload)

        expect(updateMcp).toHaveBeenCalledOnce()
        expect(updateMcp).toHaveBeenCalledWith({id: "my-server", serverType: "PUBLIC", authType: "BASIC", disabled: false})
        expect(result).toEqual(mockServer)
    })

    it("remove() calls deleteMcp with id", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        await store.remove("my-server")

        expect(deleteMcp).toHaveBeenCalledOnce()
        expect(deleteMcp).toHaveBeenCalledWith({id: "my-server"})
    })

    it("toggle() calls toggleMcp and returns updated server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp")
        const store = useMcpStore()

        const result = await store.toggle("my-server")

        expect(toggleMcp).toHaveBeenCalledOnce()
        expect(toggleMcp).toHaveBeenCalledWith({id: "my-server"})
        expect(result).toEqual({...mockServer, disabled: true})
    })
})
