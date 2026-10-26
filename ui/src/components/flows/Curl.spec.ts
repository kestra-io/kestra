import {describe, it, expect, vi} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Flow} from "../../stores/flow"

vi.mock("override/utils/route", () => ({
    baseUrl: "http://localhost:8080",
    basePath: () => "/api/v1/main",
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

import Curl from "./Curl.vue"

const flow = {
    id: "dates",
    namespace: "company.team",
    inputs: [
        {id: "d", type: "DATE", required: false},
        {id: "t", type: "TIME", required: false},
        {id: "dt", type: "DATETIME", required: false},
    ],
} as unknown as Flow

function curlFor(inputs: Record<string, unknown>) {
    const wrapper = i18nMount(Curl, {
        props: {flow, inputs, verbose: false},
        global: {stubs: {CopyToClipboard: true}},
    })
    const command = wrapper.find("code").text()
    wrapper.unmount()
    return command
}

const URL = "'http://localhost:8080/api/v1/main/executions/company.team/dates'"

describe("Curl", () => {
    it.each([
        ["left untouched", undefined],
        ["cleared", null],
        ["empty", ""],
    ])("omits optional DATE, TIME and DATETIME inputs that are %s", (_, value) => {
        expect(curlFor({d: value, t: value, dt: value}))
            .toBe(`curl -X POST -H 'Content-Type: multipart/form-data' ${URL}`)
    })

    it("omits an empty DATE input and keeps a filled TIME input", () => {
        expect(curlFor({t: new Date(2026, 9, 26, 14, 5, 9)}))
            .toBe(`curl -X POST -H 'Content-Type: multipart/form-data' -F 't=14:05:09' ${URL}`)
    })

    it("omits an empty TIME input and keeps a filled DATE input", () => {
        expect(curlFor({d: new Date(2026, 9, 26), t: null}))
            .toBe(`curl -X POST -H 'Content-Type: multipart/form-data' -F 'd=2026-10-26' ${URL}`)
    })

    it("sends a filled DATETIME input as an ISO instant", () => {
        expect(curlFor({dt: new Date(Date.UTC(2026, 9, 20, 8, 35, 9))}))
            .toBe(`curl -X POST -H 'Content-Type: multipart/form-data' -F 'dt=2026-10-20T08:35:09.000Z' ${URL}`)
    })

    it("keeps an unparsable DATETIME value as is instead of throwing", () => {
        expect(curlFor({dt: "not-a-date"}))
            .toBe(`curl -X POST -H 'Content-Type: multipart/form-data' -F 'dt=not-a-date' ${URL}`)
    })
})
