import {beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick, ref} from "vue"
import {flushPromises} from "@vue/test-utils"

const {mockSearchFlowsBySourceCode} = vi.hoisted(() => ({mockSearchFlowsBySourceCode: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({searchFlowsBySourceCode: mockSearchFlowsBySourceCode}))

import {buildUsagePattern, selectUsages, useKeyUsages, type KeyTarget} from "./useKeyUsages"
import type {SourceSearchResult} from "../utils/sourceSearchDiff"

const flow = (namespace: string, id: string, snippets: string[]): SourceSearchResult => ({
    namespace,
    id,
    editable: true,
    matches: snippets.map((snippet, index) => ({line: index + 1, column: 0, snippet})),
})

const deferred = <T>() => {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((r) => {
        resolve = r
    })
    return {promise, resolve}
}

const mountComposable = (target: KeyTarget | undefined) => {
    const source = ref(target)
    const scope = effectScope()
    const state = scope.run(() => useKeyUsages(source))!
    return {source, state, scope}
}

describe("buildUsagePattern", () => {
    const matches = (pattern: string, text: string) => new RegExp(pattern).test(text)

    it("matches single and double quoted references with optional whitespace", () => {
        const pattern = buildUsagePattern({kind: "kv", key: "my_key"})

        expect(matches(pattern, "{{ kv('my_key') }}")).toBe(true)
        expect(matches(pattern, "{{ kv(\"my_key\") }}")).toBe(true)
        expect(matches(pattern, "{{kv( 'my_key', 'ns') }}")).toBe(true)
        expect(matches(pattern, "{{ kv('my_key_other') }}")).toBe(false)
        expect(matches(pattern, "{{ secret('my_key') }}")).toBe(false)
    })

    it("uses the function matching the key kind", () => {
        expect(matches(buildUsagePattern({kind: "secret", key: "TOKEN"}), "{{ secret('TOKEN') }}")).toBe(true)
        expect(matches(buildUsagePattern({kind: "secret", key: "TOKEN"}), "{{ kv('TOKEN') }}")).toBe(false)
    })

    it("escapes regex metacharacters in the key", () => {
        const pattern = buildUsagePattern({kind: "kv", key: "a.b+c(d)[e]"})

        expect(matches(pattern, "kv('a.b+c(d)[e]')")).toBe(true)
        expect(matches(pattern, "kv('aXb+c(d)[e]')")).toBe(false)
        expect(matches(buildUsagePattern({kind: "kv", key: "a.b"}), "kv('aXb')")).toBe(false)
    })
})

describe("selectUsages", () => {
    it("keeps flows in the namespace and its descendants only", () => {
        const results = [
            flow("company", "same", ["kv('k')"]),
            flow("company.team", "child", ["kv('k')"]),
            flow("company2", "sibling-prefix", ["kv('k')"]),
            flow("other", "unrelated", ["kv('k')"]),
        ]

        const usages = selectUsages(results, "company")

        expect(usages.map((usage) => usage.id)).toEqual(["same", "child"])
    })

    it("keeps a match outside the namespace when its line names the namespace", () => {
        const results = [flow("other", "explicit", ["kv('k', 'company')", "kv('k')"])]

        const usages = selectUsages(results, "company")

        expect(usages).toEqual([{namespace: "other", id: "explicit", line: 1, snippet: "kv('k', 'company')"}])
    })

    it("strips search markers from snippets", () => {
        const usages = selectUsages([flow("company", "f", ["  {{ [mark]kv('k'[/mark]) }}  "])], "company")

        expect(usages[0].snippet).toBe("{{ kv('k') }}")
    })

    it("keeps every flow when the key has no namespace", () => {
        expect(selectUsages([flow("any", "f", ["secret('K')"])], undefined)).toHaveLength(1)
    })
})

describe("useKeyUsages", () => {
    beforeEach(() => {
        mockSearchFlowsBySourceCode.mockReset()
    })

    it("searches all namespaces with a bounded regex query", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "f", ["kv('k')"])], total: 1})

        const {state} = mountComposable({kind: "kv", namespace: "company", key: "k"})
        await flushPromises()

        const params = mockSearchFlowsBySourceCode.mock.calls[0][0]
        expect(params).toMatchObject({regex: true, caseSensitive: true, page: 1, q: buildUsagePattern({kind: "kv", key: "k"})})
        expect(params.namespace).toBeUndefined()
        expect(params.size).toBeLessThanOrEqual(100)
        expect(state.status.value).toBe("done")
        expect(state.usages.value).toHaveLength(1)
        expect(state.truncated.value).toBe(false)
    })

    it("reports truncation when the server holds more flows than returned", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "f", ["kv('k')"])], total: 250})

        const {state} = mountComposable({kind: "kv", namespace: "company", key: "k"})
        await flushPromises()

        expect(state.truncated.value).toBe(true)
        expect(state.total.value).toBe(250)
        expect(state.shown.value).toBe(1)
    })

    it("exposes the failure and clears usages", async () => {
        mockSearchFlowsBySourceCode.mockRejectedValue(new Error("boom"))

        const {state} = mountComposable({kind: "kv", namespace: "company", key: "k"})
        await flushPromises()

        expect(state.status.value).toBe("failed")
        expect(state.errorMessage.value).toBe("boom")
        expect(state.usages.value).toEqual([])
    })

    it("ignores a stale response when the key changes", async () => {
        const first = deferred<unknown>()
        const second = deferred<unknown>()
        mockSearchFlowsBySourceCode.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

        const {source, state} = mountComposable({kind: "kv", namespace: "company", key: "old"})
        source.value = {kind: "kv", namespace: "company", key: "new"}
        await nextTick()

        second.resolve({results: [flow("company", "new-flow", ["kv('new')"])], total: 1})
        await flushPromises()
        first.resolve({results: [flow("company", "old-flow", ["kv('old')"])], total: 1})
        await flushPromises()

        expect(state.usages.value.map((usage) => usage.id)).toEqual(["new-flow"])
    })

    it("ignores a response arriving after the scope is disposed", async () => {
        const pending = deferred<unknown>()
        mockSearchFlowsBySourceCode.mockReturnValueOnce(pending.promise)

        const {state, scope} = mountComposable({kind: "kv", namespace: "company", key: "k"})
        scope.stop()
        pending.resolve({results: [flow("company", "f", ["kv('k')"])], total: 1})
        await flushPromises()

        expect(state.usages.value).toEqual([])
    })
})
