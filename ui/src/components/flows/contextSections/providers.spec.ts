import {beforeEach, describe, expect, it, vi} from "vitest"

const listKeysWithInheritence = vi.fn()
const listAllKeys = vi.fn()
vi.mock("@kestra-io/kestra-sdk/kv", () => ({listKeysWithInheritence, listAllKeys}))

const inheritedSecrets = vi.fn()
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({inheritedSecrets}))

const searchNamespaceFiles = vi.fn()
vi.mock("@kestra-io/kestra-sdk/files", () => ({searchNamespaceFiles}))

beforeEach(() => {
    vi.clearAllMocks()
})

describe("kvContextSectionProvider", () => {
    it("merges the namespace's own keys (listAllKeys) with the inherited ones (listKeysWithInheritence)", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [{key: "LAST_RUN_DATE"}, {key: "REGION_CODE"}], total: 2})
        listKeysWithInheritence.mockResolvedValue([{key: "PARENT_KEY"}])

        const section = await kvContextSectionProvider({namespace: "qa.nocode"})

        expect(section?.chips).toEqual([
            {label: "LAST_RUN_DATE", expr: "{{ kv('LAST_RUN_DATE') }}"},
            {label: "REGION_CODE", expr: "{{ kv('REGION_CODE') }}"},
            {label: "PARENT_KEY", expr: "{{ kv('PARENT_KEY') }}"},
        ])
    })

    it("dedupes a key present in both listAllKeys and listKeysWithInheritence", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [{key: "SHARED"}], total: 1})
        listKeysWithInheritence.mockResolvedValue([{key: "SHARED"}])

        const section = await kvContextSectionProvider({namespace: "team.a"})

        expect(section?.chips).toEqual([{label: "SHARED", expr: "{{ kv('SHARED') }}"}])
    })

    it("returns null when neither call has any keys", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [], total: 0})
        listKeysWithInheritence.mockResolvedValue([])

        expect(await kvContextSectionProvider({namespace: "team.a"})).toBeNull()
    })

    it("filters listAllKeys by the namespace and omits tenant entirely when absent", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [], total: 0})
        listKeysWithInheritence.mockResolvedValue([])

        await kvContextSectionProvider({namespace: "team.a"})

        expect(listAllKeys).toHaveBeenCalledWith(
            {
                size: 1000,
                filters: [{field: "namespace", operation: "EQUALS", value: "team.a"}],
            },
            {showMessageOnError: false, ignoreNotFound: true},
        )
        expect(listKeysWithInheritence).toHaveBeenCalledWith(
            {namespace: "team.a"},
            {showMessageOnError: false, ignoreNotFound: true},
        )
    })

    it("never raises the shared error toast — a namespace with no KV store yet must fail silently", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [], total: 0})
        listKeysWithInheritence.mockResolvedValue([])

        await kvContextSectionProvider({namespace: "team.a"})

        for (const call of [...listAllKeys.mock.calls, ...listKeysWithInheritence.mock.calls]) {
            expect(call[1]).toMatchObject({showMessageOnError: false})
        }
    })

    it("escapes a quote in the key so it cannot break out of the Pebble string literal", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [{key: "it's a key"}], total: 1})
        listKeysWithInheritence.mockResolvedValue([])

        const section = await kvContextSectionProvider({namespace: "team.a"})

        expect(section?.chips).toEqual([{label: "it's a key", expr: "{{ kv('it\\'s a key') }}"}])
    })

    it("returns a translation key rather than an already-translated label, so caching it never freezes one UI language", async () => {
        const {kvContextSectionProvider} = await import("./providers")
        listAllKeys.mockResolvedValue({results: [{key: "LAST_RUN_DATE"}], total: 1})
        listKeysWithInheritence.mockResolvedValue([])

        const section = await kvContextSectionProvider({namespace: "team.a"})

        expect(section?.labelKey).toBe("block_editor.namespace_kv")
        expect(section).not.toHaveProperty("label")
    })
})

describe("secretsContextSectionProvider", () => {
    it("flattens and dedupes secret names across the inherited namespaces", async () => {
        const {secretsContextSectionProvider} = await import("./providers")
        inheritedSecrets.mockResolvedValue({"team.a": ["SLACK_WEBHOOK"], "team": ["SLACK_WEBHOOK", "AWS_KEY"]})

        const section = await secretsContextSectionProvider({namespace: "team.a"})

        expect(section?.chips).toEqual([
            {label: "SLACK_WEBHOOK", expr: "{{ secret('SLACK_WEBHOOK') }}"},
            {label: "AWS_KEY", expr: "{{ secret('AWS_KEY') }}"},
        ])
    })

    it("returns null when there are no secrets", async () => {
        const {secretsContextSectionProvider} = await import("./providers")
        inheritedSecrets.mockResolvedValue({"team.a": []})

        expect(await secretsContextSectionProvider({namespace: "team.a"})).toBeNull()
    })

    it("omits tenant entirely rather than passing it as undefined, so the SDK falls back to the global tenant", async () => {
        const {secretsContextSectionProvider} = await import("./providers")
        inheritedSecrets.mockResolvedValue({"team.a": []})

        await secretsContextSectionProvider({namespace: "team.a"})

        expect(inheritedSecrets).toHaveBeenCalledWith(
            {namespace: "team.a"},
            {showMessageOnError: false, ignoreNotFound: true},
        )
    })

    it("never raises the shared error toast — a namespace with no secrets access must fail silently", async () => {
        const {secretsContextSectionProvider} = await import("./providers")
        inheritedSecrets.mockResolvedValue({"team.a": []})

        await secretsContextSectionProvider({namespace: "team.a"})

        expect(inheritedSecrets.mock.calls[0][1]).toMatchObject({showMessageOnError: false})
    })

    it("escapes a quote in the secret name so it cannot break out of the Pebble string literal", async () => {
        const {secretsContextSectionProvider} = await import("./providers")
        inheritedSecrets.mockResolvedValue({"team.a": ["it's a secret"]})

        const section = await secretsContextSectionProvider({namespace: "team.a"})

        expect(section?.chips).toEqual([{label: "it's a secret", expr: "{{ secret('it\\'s a secret') }}"}])
    })
})

describe("namespaceFilesContextSectionProvider", () => {
    it("builds the read()/fileURI() chips per file", async () => {
        const {namespaceFilesContextSectionProvider} = await import("./providers")
        searchNamespaceFiles.mockResolvedValue(["queries/idle_ec2.sql"])

        const section = await namespaceFilesContextSectionProvider({namespace: "team.a"})

        expect(section?.chips).toEqual([{label: "read('queries/idle_ec2.sql')", expr: "{{ read('queries/idle_ec2.sql') }}", groupKey: "queries/idle_ec2.sql"}])
    })

    it("returns null when the namespace has no files", async () => {
        const {namespaceFilesContextSectionProvider} = await import("./providers")
        searchNamespaceFiles.mockResolvedValue([])

        expect(await namespaceFilesContextSectionProvider({namespace: "team.a"})).toBeNull()
    })

    it("searches with the match-all query, omitting tenant entirely when absent", async () => {
        const {namespaceFilesContextSectionProvider} = await import("./providers")
        searchNamespaceFiles.mockResolvedValue([])

        await namespaceFilesContextSectionProvider({namespace: "team.a"})

        expect(searchNamespaceFiles).toHaveBeenCalledWith(
            {namespace: "team.a", q: "*"},
            {showMessageOnError: false, ignoreNotFound: true},
        )
    })

    it("never raises the shared error toast — a namespace with no files must fail silently", async () => {
        const {namespaceFilesContextSectionProvider} = await import("./providers")
        searchNamespaceFiles.mockResolvedValue([])

        await namespaceFilesContextSectionProvider({namespace: "team.a"})

        expect(searchNamespaceFiles.mock.calls[0][1]).toMatchObject({showMessageOnError: false})
    })
})
