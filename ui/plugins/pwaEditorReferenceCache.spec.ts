import {describe, expect, it} from "vitest"
import {EDITOR_REFERENCE_PATTERN} from "../src/utils/editorReferenceRequests"
import {editorReferenceCache} from "./pwaEditorReferenceCache"

const ORIGIN = "https://kestra.example"

describe("EDITOR_REFERENCE_PATTERN", () => {
    it("is the pattern the service worker caches on", () => {
        expect(editorReferenceCache.urlPattern).toBe(EDITOR_REFERENCE_PATTERN)
    })

    it.each([
        "/api/v1/plugins",
        "/api/v1/plugins/schemas/flow",
        "/api/v1/plugins/schemas/task?arrayOf=false",
        "/api/v1/plugins/icons",
        "/api/v1/plugins/icons/groups",
        "/api/v1/plugins/icons/io.kestra.plugin.core.log.Log/icon.svg",
        "/api/v1/plugins/groups/subgroups",
        "/api/v1/plugins/inputs/STRING",
        "/api/v1/plugins/io.kestra.plugin.core.log.Log?all=true",
        "/api/v1/plugins/io.kestra.plugin.core.log.Log/versions/1.2.0",
        "/api/v1/acme/plugins/schemas/flow",
    ])("keeps %s for an unreachable server", (path) => {
        expect(EDITOR_REFERENCE_PATTERN.test(ORIGIN + path)).toBe(true)
    })

    it.each([
        "/api/v1/plugins/install",
        "/api/v1/plugins/install/4kZ1",
        "/api/v1/plugins/auto-install/detect",
        "/api/v1/plugins/pluginUiManifest",
        "/api/v1/plugins/io.kestra.plugin.core/pluginUi/remoteEntry.js",
        "/api/v1/main/plugins/io.kestra.plugin.core.log.Log/endpoints/logs/exec/run",
        "/api/v1/main/flows/io.kestra/plugins",
        "/api/v1/main/flows/validate",
    ])("leaves %s to the network", (path) => {
        expect(EDITOR_REFERENCE_PATTERN.test(ORIGIN + path)).toBe(false)
    })
})
