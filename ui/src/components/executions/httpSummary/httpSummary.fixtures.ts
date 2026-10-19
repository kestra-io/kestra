import type {Execution, Task} from "@kestra-io/kestra-sdk"
import {HTTP_DOWNLOAD_TYPE, HTTP_REQUEST_TYPE} from "./httpSummary"

export const requestTask = {id: "call_api", type: HTTP_REQUEST_TYPE} as Task
export const downloadTask = {id: "fetch_file", type: HTTP_DOWNLOAD_TYPE} as Task
export const execution = {id: "exec1", taskRunList: []} as unknown as Execution

const request = {
    method: "POST",
    uri: "https://api.example.com/v1/items?token=***",
    host: "api.example.com",
    path: "/v1/items",
    queryParamNames: ["token", "page"],
    headerNames: ["Authorization", "Content-Type"],
    contentType: "application/json",
    bodyBytes: 42,
    hasBody: true,
    formFieldNames: [],
    auth: "BEARER",
    timeout: "PT30S",
    followRedirects: true,
}

export const successOutputs = {
    uri: "https://api.example.com/v1/items?token=***",
    code: 200,
    reason: "OK",
    request,
    contentType: "application/json",
    contentLength: 2048,
    durationMs: 183,
    finalUri: "https://api.example.com/v1/items?token=***",
    redirects: [],
    protocol: "HTTP/1.1",
    retries: 0,
    headers: {"content-type": ["application/json"]},
    body: {id: 1, name: "item"},
}

export const redirectRetryOutputs = {
    ...successOutputs,
    finalUri: "https://api.example.com/v2/items",
    redirects: [
        {code: 301, location: "https://api.example.com/v2/items"},
        {code: 302, location: "https://api.example.com/v2/items"},
    ],
    retries: 2,
    durationMs: 2400,
}

export const redactedHeadersOutputs = {
    ...successOutputs,
    headers: {"set-cookie": ["***"], "content-type": ["application/json"], "x-request-id": ["abc-123"]},
}

export const encryptedBodyOutputs = {
    ...successOutputs,
    body: undefined,
    encryptedBody: {value: "ciphertext"},
}

export const noRequestOutputs = {
    uri: "https://api.example.com/v1/items",
    code: 204,
    reason: "No Content",
    contentLength: 0,
    durationMs: 51,
}

export const downloadOutputs = {
    uri: "kestra:///company/flow/executions/exec1/tasks/fetch_file/abc/data.csv",
    code: 200,
    reason: "OK",
    length: 1048576,
    contentType: "text/csv",
    contentLength: 1048576,
    durationMs: 900,
    finalUri: "https://files.example.com/data.csv",
    headers: {},
    request,
}
