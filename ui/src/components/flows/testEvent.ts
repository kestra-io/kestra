import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

import {webhookUrl} from "../../utils/webhook"

// Labels the created execution `system.from: testEvent` so it can be filtered out of metrics.
export const TEST_EVENT_HEADER = "X-Kestra-Test-Event"

export const SAMPLE_TEST_EVENT_PAYLOAD = `{
  "order_id": 1042,
  "customer": "ACME",
  "total": 187.5
}`

export interface TestEventResult {
    status: number;
    ok: boolean;
    executionId?: string;
    url: string;
    error?: string;
}

export async function sendWebhookTestEvent(options: {
    namespace: string;
    flowId: string;
    key: string;
    payload: string;
    headers?: Record<string, string>;
}): Promise<TestEventResult> {
    const url = webhookUrl({namespace: options.namespace, id: options.flowId, key: options.key})

    try {
        const response = await ExecutionsAPI.triggerExecutionByPostWebhook(
            {namespace: options.namespace, id: options.flowId, key: options.key},
            {
                body: options.payload,
                headers: {
                    "Content-Type": "application/json",
                    [TEST_EVENT_HEADER]: "true",
                    ...(options.headers ?? {}),
                },
            } as Parameters<typeof ExecutionsAPI.triggerExecutionByPostWebhook>[1],
        ) as {id?: string} | undefined

        return {status: 200, ok: true, executionId: response?.id, url}
    } catch (error: any) {
        return {
            status: error?.status ?? error?.response?.status ?? 0,
            ok: false,
            url,
            error: error?.message,
        }
    }
}

/** Parses one `Name: value` pair per line. */
export function parseHeaderLines(raw: string): Record<string, string> {
    return raw
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .reduce<Record<string, string>>((headers, line) => {
            const separator = line.indexOf(":")
            if (separator > 0) {
                headers[line.slice(0, separator).trim()] = line.slice(separator + 1).trim()
            }
            return headers
        }, {})
}
