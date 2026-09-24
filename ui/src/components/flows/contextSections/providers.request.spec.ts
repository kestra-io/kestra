import {afterEach, beforeAll, describe, expect, it, vi} from "vitest"
import {configureClient} from "@kestra-io/kestra-sdk"
import {kvContextSectionProvider, secretsContextSectionProvider, namespaceFilesContextSectionProvider} from "./providers"

const t = (key: string) => key

// Node's native fetch/Request (used under Vitest) has no browsing-context base URL to resolve a
// relative path against, unlike a real browser — an absolute baseUrl makes the resulting Request
// constructible so its .url can be asserted on.
beforeAll(() => {
    configureClient({baseUrl: "https://example.test"})
})

function stubFetch(body: unknown) {
    const fetchMock = vi.fn(async (_request: Request) =>
        new Response(JSON.stringify(body), {status: 200, headers: {"content-type": "application/json"}}),
    )
    vi.stubGlobal("fetch", fetchMock)
    return fetchMock
}

function stubFetchByUrl(responses: Array<{when: (url: string) => boolean, body: unknown}>) {
    const fetchMock = vi.fn(async (request: Request) => {
        const match = responses.find(({when}) => when(request.url))
        return new Response(JSON.stringify(match?.body ?? null), {status: 200, headers: {"content-type": "application/json"}})
    })
    vi.stubGlobal("fetch", fetchMock)
    return fetchMock
}

afterEach(() => {
    vi.unstubAllGlobals()
})

describe("context section providers build a real, tenant-resolved request URL", () => {
    it("kvContextSectionProvider never leaves an unresolved {tenant} placeholder, on either call", async () => {
        const fetchMock = stubFetch([])
        await kvContextSectionProvider({namespace: "team.a", t})

        const urls = fetchMock.mock.calls.map(([request]) => (request as Request).url)
        expect(urls).toHaveLength(2)
        for (const url of urls) {
            expect(url).not.toContain("{tenant}")
            expect(url).not.toContain("undefined")
        }
        expect(urls.some(url => url.includes("/api/v1/main/kv?"))).toBe(true)
        expect(urls.some(url => url.includes("/api/v1/main/namespaces/team.a/kv/inheritance"))).toBe(true)
    })

    it("secretsContextSectionProvider never leaves an unresolved {tenant} placeholder", async () => {
        const fetchMock = stubFetch({})
        await secretsContextSectionProvider({namespace: "team.a", t})

        const url = (fetchMock.mock.calls[0][0] as Request).url
        expect(url).not.toContain("{tenant}")
        expect(url).not.toContain("undefined")
        expect(url).toContain("/api/v1/main/namespaces/team.a/inherited-secrets")
    })

    it("namespaceFilesContextSectionProvider searches with the match-all query and never leaves an unresolved {tenant} placeholder", async () => {
        const fetchMock = stubFetch([])
        await namespaceFilesContextSectionProvider({namespace: "team.a", t})

        const url = (fetchMock.mock.calls[0][0] as Request).url
        expect(url).not.toContain("{tenant}")
        expect(url).not.toContain("undefined")
        expect(url).toContain("/api/v1/main/namespaces/team.a/files/search")
        expect(url).toContain("q=*")
    })
})

describe("context section providers build a section from a realistic, non-empty response", () => {
    it("kvContextSectionProvider merges the namespace's own keys with the inherited ones", async () => {
        stubFetchByUrl([
            {
                when: url => url.includes("/kv?"),
                body: {results: [{namespace: "qa.nocode", key: "LAST_RUN_DATE"}, {namespace: "qa.nocode", key: "REGION_CODE"}], total: 2},
            },
            {when: url => url.includes("/kv/inheritance"), body: []},
        ])

        const section = await kvContextSectionProvider({namespace: "qa.nocode", t})

        expect(section?.chips).toEqual([
            {label: "LAST_RUN_DATE", expr: "{{ kv('LAST_RUN_DATE') }}"},
            {label: "REGION_CODE", expr: "{{ kv('REGION_CODE') }}"},
        ])
    })

    it("secretsContextSectionProvider builds a chip per inherited secret name", async () => {
        stubFetch({"qa.nocode": ["SLACK_WEBHOOK"]})

        const section = await secretsContextSectionProvider({namespace: "qa.nocode", t})

        expect(section?.chips).toEqual([{label: "SLACK_WEBHOOK", expr: "{{ secret('SLACK_WEBHOOK') }}"}])
    })

    it("namespaceFilesContextSectionProvider builds a chip per file, escaping a quote in a real path", async () => {
        stubFetch(["/fixtures/sample_response.json", "/queries/idle_ec2.sql", "/queries/o'brien report.sql"])

        const section = await namespaceFilesContextSectionProvider({namespace: "qa.nocode", t})

        expect(section?.chips).toEqual([
            {label: "fileURI('fixtures/sample_response.json')", expr: "{{ fileURI('fixtures/sample_response.json') }}"},
            {label: "read('queries/idle_ec2.sql')", expr: "{{ read('queries/idle_ec2.sql') }}"},
            {label: "read('queries/o'brien report.sql')", expr: "{{ read('queries/o\\'brien report.sql') }}"},
        ])
    })
})
