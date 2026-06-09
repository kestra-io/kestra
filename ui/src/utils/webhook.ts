import {apiUrl} from "override/utils/route"

export const WEBHOOK_TRIGGER_TYPE = "io.kestra.plugin.core.trigger.Webhook"

export interface WebhookUrlParams {
    namespace: string;
    id: string;
    key: string;
}

export function webhookUrl({namespace, id, key}: WebhookUrlParams): string {
    const url = `${apiUrl()}/executions/webhook/${encodeURIComponent(namespace)}/${encodeURIComponent(id)}/${encodeURIComponent(key)}`
    return /^https?:\/\//.test(url) ? url : `${window.location.origin}${url}`
}
