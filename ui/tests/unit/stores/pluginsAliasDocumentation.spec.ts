import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

import type {Plugin} from "../../../src/utils/pluginUtils"
import type {usePluginsStore} from "../../../src/stores/plugins"

const pluginDocumentationMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginDocumentation: pluginDocumentationMock,
    pluginDocumentationFromVersion: vi.fn(),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/stores/api", () => ({
    API_URL: "https://api.kestra.io",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const CANONICAL = "io.kestra.plugin.docker.cli.Run"
const ALIAS = "io.kestra.plugin.docker.Run"

const DOCKER: Plugin = {
    name: "docker",
    title: "Docker",
    group: "io.kestra.plugin.docker",
    subGroup: "io.kestra.plugin.docker.cli",
    aliases: [ALIAS],
    tasks: [{cls: CANONICAL, deprecated: false}],
}

describe("plugins store documentation for aliased types", () => {
    let store: ReturnType<typeof usePluginsStore>

    beforeEach(async () => {
        vi.resetModules()
        pluginDocumentationMock.mockReset()
        pluginDocumentationMock.mockResolvedValue({schema: {properties: {properties: {}}}})
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
        store.plugins = [DOCKER]
    })

    it("loads the documentation of a type registered as an alias", async () => {
        await store.updateDocumentation({cls: ALIAS})

        expect(pluginDocumentationMock).toHaveBeenCalledTimes(1)
        expect(pluginDocumentationMock.mock.calls[0][0]).toMatchObject({cls: ALIAS})
        expect(store.editorPlugin?.cls).toBe(ALIAS)
    })

    it("still ignores a type that is neither declared nor aliased", async () => {
        await store.updateDocumentation({cls: "io.kestra.plugin.unknown.Task"})

        expect(pluginDocumentationMock).not.toHaveBeenCalled()
        expect(store.editorPlugin).toBeUndefined()
    })
})
