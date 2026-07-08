import {describe, it, expect} from "vitest"
import {
    filterDefaultsCoveredMarkers,
    isPropertyCoveredByDefaults,
    type EffectiveDefault,
    type MarkerLike,
} from "../../../src/composables/monaco/languages/pluginDefaultsMarkers"

// A flow whose Download task omits the required `uri`, supplied via flow-level pluginDefaults.
const FLOW = `id: test
namespace: io.kestra.unittest
tasks:
  - id: download
    type: io.kestra.plugin.core.http.Download
pluginDefaults:
  - type: io.kestra.plugin.core.http.Download
    values:
      uri: https://kestra.io
`

// Marker positioned on the download task's `type:` line (line 5), as monaco-yaml
// reports missing-required diagnostics against the enclosing mapping.
function missingUriMarker(): MarkerLike {
    return {message: "Missing property \"uri\".", startLineNumber: 5, startColumn: 5}
}

describe("isPropertyCoveredByDefaults", () => {
    const defaults: EffectiveDefault[] = [
        {type: "io.kestra.plugin.core.http.Download", values: {uri: "https://kestra.io"}},
    ]

    it("covers exact type match with the property present", () => {
        expect(isPropertyCoveredByDefaults("uri", "io.kestra.plugin.core.http.Download", defaults)).toBe(true)
    })

    it("does not cover a property the default does not supply", () => {
        expect(isPropertyCoveredByDefaults("method", "io.kestra.plugin.core.http.Download", defaults)).toBe(false)
    })

    it("matches by type prefix (backend semantics)", () => {
        const prefixDefaults: EffectiveDefault[] = [{type: "io.kestra.plugin.core.http", values: {uri: "x"}}]
        expect(isPropertyCoveredByDefaults("uri", "io.kestra.plugin.core.http.Download", prefixDefaults)).toBe(true)
    })

    it("does not match an unrelated type", () => {
        expect(isPropertyCoveredByDefaults("uri", "io.kestra.plugin.core.log.Log", defaults)).toBe(false)
    })

    it("ignores defaults without a values map", () => {
        expect(isPropertyCoveredByDefaults("uri", "io.kestra.plugin.core.http.Download", [{type: "io.kestra.plugin.core.http.Download"}])).toBe(false)
    })
})

describe("filterDefaultsCoveredMarkers", () => {
    it("drops a missing-property marker covered by a flow-level default", () => {
        const defaults: EffectiveDefault[] = [
            {type: "io.kestra.plugin.core.http.Download", values: {uri: "https://kestra.io"}},
        ]
        const result = filterDefaultsCoveredMarkers([missingUriMarker()], FLOW, defaults)
        expect(result).toHaveLength(0)
    })

    it("keeps a missing-property marker when no default supplies it", () => {
        const defaults: EffectiveDefault[] = [
            {type: "io.kestra.plugin.core.http.Download", values: {method: "GET"}},
        ]
        const result = filterDefaultsCoveredMarkers([missingUriMarker()], FLOW, defaults)
        expect(result).toHaveLength(1)
    })

    it("keeps a missing-property marker for a different task type", () => {
        const defaults: EffectiveDefault[] = [
            {type: "io.kestra.plugin.core.log.Log", values: {uri: "x"}},
        ]
        const result = filterDefaultsCoveredMarkers([missingUriMarker()], FLOW, defaults)
        expect(result).toHaveLength(1)
    })

    it("preserves non missing-property markers untouched", () => {
        const other: MarkerLike = {message: "Incorrect type. Expected \"string\".", startLineNumber: 5, startColumn: 5}
        const defaults: EffectiveDefault[] = [
            {type: "io.kestra.plugin.core.http.Download", values: {uri: "https://kestra.io"}},
        ]
        const result = filterDefaultsCoveredMarkers([other, missingUriMarker()], FLOW, defaults)
        expect(result).toEqual([other])
    })

    it("is idempotent — re-running on its own output removes nothing", () => {
        const defaults: EffectiveDefault[] = [
            {type: "io.kestra.plugin.core.http.Download", values: {uri: "https://kestra.io"}},
        ]
        const once = filterDefaultsCoveredMarkers([missingUriMarker()], FLOW, defaults)
        const twice = filterDefaultsCoveredMarkers(once, FLOW, defaults)
        expect(twice).toEqual(once)
    })

    it("returns markers unchanged when there are no defaults", () => {
        const markers = [missingUriMarker()]
        expect(filterDefaultsCoveredMarkers(markers, FLOW, [])).toBe(markers)
    })
})
