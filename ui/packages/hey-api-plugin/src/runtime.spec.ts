import {describe, expect, it} from "vitest"

import {createConfigureClient, type ConfigurableFetchClient} from "./runtime"
import {EnterpriseFeatureError, SdkVersionMismatchError} from "./errors"

type ErrorInterceptor = (error: unknown, response: Response | undefined, request: Request | undefined, opts: any) => unknown

/** Builds a fake @hey-api/client-fetch client that captures the registered error interceptor. */
function buildFakeClient(): { client: ConfigurableFetchClient; getErrorInterceptor: () => ErrorInterceptor } {
    let errorInterceptor: ErrorInterceptor = (error) => error

    const client: ConfigurableFetchClient = {
        setConfig: () => undefined,
        interceptors: {
            request: {clear: () => undefined, use: () => undefined},
            response: {clear: () => undefined, use: () => undefined},
            error: {
                clear: () => undefined,
                use: (fn) => {
                    errorInterceptor = fn as ErrorInterceptor
                },
            },
        },
    }

    return {client, getErrorInterceptor: () => errorInterceptor}
}

const formDataBodySerializer = {bodySerializer: () => undefined}

const enterpriseFeature = {
    matchRoute: (method: string, path: string) =>
        method === "GET" && path === "/api/v1/{tenant}/audit-logs/search" ? {feature: "audit-logs"} : undefined,
    docsUrl: (feature: string) => `https://kestra.io/docs/enterprise/${feature}`,
    contactSalesUrl: () => "https://kestra.io/demo",
}

function fakeRequest(): Request {
    return new Request("https://example.com/api/v1/main/audit-logs/search")
}

function fakeResponse(status: number, headers: Record<string, string> = {}): Response {
    return new Response(null, {status, headers})
}

describe("createConfigureClient 404 disambiguation", () => {
    it("never returns EnterpriseFeatureError when X-Kestra-Entity is present, regardless of edition", () => {
        const {client, getErrorInterceptor} = buildFakeClient()
        createConfigureClient(client, formDataBodySerializer, enterpriseFeature)()

        const result = getErrorInterceptor()(
            {message: "Not Found"},
            fakeResponse(404, {"X-Kestra-Edition": "EE", "X-Kestra-Entity": "AUDITLOG"}),
            fakeRequest(),
            {url: "/api/v1/{tenant}/audit-logs/search"},
        )

        expect(result).not.toBeInstanceOf(EnterpriseFeatureError)
        expect(result).not.toBeInstanceOf(SdkVersionMismatchError)
    })

    it("returns SdkVersionMismatchError when the route matches, entity is absent, and edition is EE", () => {
        const {client, getErrorInterceptor} = buildFakeClient()
        createConfigureClient(client, formDataBodySerializer, enterpriseFeature)()

        const result = getErrorInterceptor()(
            {message: "Not Found"},
            fakeResponse(404, {"X-Kestra-Edition": "EE"}),
            fakeRequest(),
            {url: "/api/v1/{tenant}/audit-logs/search"},
        )

        expect(result).toBeInstanceOf(SdkVersionMismatchError)
        expect((result as SdkVersionMismatchError).feature).toBe("audit-logs")
    })

    it("returns EnterpriseFeatureError when the route matches and edition is OSS", () => {
        const {client, getErrorInterceptor} = buildFakeClient()
        createConfigureClient(client, formDataBodySerializer, enterpriseFeature)()

        const result = getErrorInterceptor()(
            {message: "Not Found"},
            fakeResponse(404, {"X-Kestra-Edition": "OSS"}),
            fakeRequest(),
            {url: "/api/v1/{tenant}/audit-logs/search"},
        )

        expect(result).toBeInstanceOf(EnterpriseFeatureError)
    })

    it("returns EnterpriseFeatureError when the route matches and the edition header is absent (older server)", () => {
        const {client, getErrorInterceptor} = buildFakeClient()
        createConfigureClient(client, formDataBodySerializer, enterpriseFeature)()

        const result = getErrorInterceptor()(
            {message: "Not Found"},
            fakeResponse(404),
            fakeRequest(),
            {url: "/api/v1/{tenant}/audit-logs/search"},
        )

        expect(result).toBeInstanceOf(EnterpriseFeatureError)
    })

    it("does not throw an EE error for a non-matching route even with entity absent", () => {
        const {client, getErrorInterceptor} = buildFakeClient()
        createConfigureClient(client, formDataBodySerializer, enterpriseFeature)()

        const result = getErrorInterceptor()(
            {message: "Not Found"},
            fakeResponse(404, {"X-Kestra-Edition": "OSS"}),
            fakeRequest(),
            {url: "/api/v1/{tenant}/flows/{namespace}/{id}"},
        )

        expect(result).not.toBeInstanceOf(EnterpriseFeatureError)
        expect(result).not.toBeInstanceOf(SdkVersionMismatchError)
    })
})
