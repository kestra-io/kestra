import {describe, it, expect, vi, beforeEach} from "vitest";
import {setActivePinia, createPinia} from "pinia";

const BASE_URL = "http://localhost/api/v1/main";

const mockServer = {
    id: "my-server",
    serverType: "PRIVATE" as const,
    authType: "BASIC" as const,
    enabled: true,
    isDefault: false,
};

const axiosGet = vi.fn().mockResolvedValue({data: {results: [mockServer], total: 1}});
const axiosPost = vi.fn().mockResolvedValue({data: mockServer});
const axiosPut = vi.fn().mockResolvedValue({data: mockServer});
const axiosDelete = vi.fn().mockResolvedValue({data: undefined});
const axiosPatch = vi.fn().mockResolvedValue({data: {...mockServer, enabled: false}});

vi.mock("axios", () => ({
    default: {
        get: axiosGet,
        post: axiosPost,
        put: axiosPut,
        delete: axiosDelete,
        patch: axiosPatch,
    },
}));

vi.mock("override/utils/route", () => ({
    apiUrl: () => BASE_URL,
}));

describe("mcp store", () => {
    beforeEach(() => {
        vi.resetModules();
        axiosGet.mockClear();
        axiosPost.mockClear();
        axiosPut.mockClear();
        axiosDelete.mockClear();
        axiosPatch.mockClear();
        setActivePinia(createPinia());
        localStorage.clear();
    });

    it("list() calls GET /mcp/servers with withCredentials and returns data", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp");
        const store = useMcpStore();

        const result = await store.list();

        expect(axiosGet).toHaveBeenCalledOnce();
        expect(axiosGet).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers`, {withCredentials: true});
        expect(result).toEqual({results: [mockServer], total: 1});
    });

    it("create() calls POST /mcp/servers with payload and withCredentials and returns server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp");
        const store = useMcpStore();

        const payload = {id: "my-server", serverType: "PRIVATE" as const, authType: "BASIC" as const, enabled: true};
        const result = await store.create(payload);

        expect(axiosPost).toHaveBeenCalledOnce();
        expect(axiosPost).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers`, payload, {withCredentials: true});
        expect(result).toEqual(mockServer);
    });

    it("update() calls PUT /mcp/servers/{id} with payload and withCredentials and returns server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp");
        const store = useMcpStore();

        const payload = {id: "my-server", serverType: "PUBLIC" as const, authType: "BASIC" as const, enabled: true};
        const result = await store.update("my-server", payload);

        expect(axiosPut).toHaveBeenCalledOnce();
        expect(axiosPut).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server`, payload, {withCredentials: true});
        expect(result).toEqual(mockServer);
    });

    it("remove() calls DELETE /mcp/servers/{id} with withCredentials", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp");
        const store = useMcpStore();

        await store.remove("my-server");

        expect(axiosDelete).toHaveBeenCalledOnce();
        expect(axiosDelete).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server`, {withCredentials: true});
    });

    it("toggle() calls PATCH /mcp/servers/{id}/toggle with withCredentials and returns updated server", async () => {
        const {useMcpStore} = await import("../../../src/stores/mcp");
        const store = useMcpStore();

        const result = await store.toggle("my-server");

        expect(axiosPatch).toHaveBeenCalledOnce();
        expect(axiosPatch).toHaveBeenCalledWith(`${BASE_URL}/mcp/servers/my-server/toggle`, undefined, {withCredentials: true});
        expect(result).toEqual({...mockServer, enabled: false});
    });
});
