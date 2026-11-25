import {afterEach, describe, expect, it, vi} from "vitest"

import {createClientFacade} from "./client-facade"

describe("createClientFacade", () => {
    afterEach(() => vi.unstubAllGlobals())

    it("keeps what an error interceptor attached to the response of a failed call", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
            JSON.stringify({type: "about:blank", title: "Internal Server Error", status: 500}),
            {status: 500, headers: {"content-type": "application/problem+json"}},
        )))
        const attachRequest = (_error: unknown, _response: Response | undefined, request: Request) =>
            Object.assign(new Error("500 Internal Server Error"), {response: {config: {method: request.method, url: request.url}}})
        const {useClient} = createClientFacade(
            {interceptors: {request: {fns: []}, response: {fns: []}, error: {fns: [attachRequest]}}},
            {bodySerializer: () => undefined},
        )

        await expect(useClient().post("http://localhost/api/v1/main/dashboards/charts/state-counts", {})).rejects.toMatchObject({
            response: {
                status: 500,
                data: {title: "Internal Server Error"},
                config: {method: "POST", url: "http://localhost/api/v1/main/dashboards/charts/state-counts"},
            },
        })
    })
})
