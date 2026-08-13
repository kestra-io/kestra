import {KsMessageBox} from "@kestra-io/design-system"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

/**
 * The mechanics shared by every artefact-draft apply path — confirm, report, read the target out of
 * the YAML, recognise a create that should have been an update. `useApplyDraft` uses them for flows
 * and dashboards; the EE-only draft actions (`override/`) use the same ones so an EE artefact
 * confirms and fails exactly like an OSS one.
 */

/** Minimal shape of vue-i18n's `t`, so callers pass their own bound translator. */
type Translate = (key: string, named?: Record<string, unknown>) => string

/**
 * Per-request client option (the SDK endpoints' SECOND arg, spread into the request options and read
 * by the global error interceptor). `showMessageOnError: false` opts the call out of the global error
 * toast — failures are handled locally: a create that hits "already exists" is an expected step of the
 * create→update fallback, and any real failure gets our own alert.
 */
export const SILENT_REQUEST = {showMessageOnError: false}

/** Confirm an apply; resolves false when the user dismisses the dialog. */
export function confirmApply(t: Translate, message: string, title: string): Promise<boolean> {
    return KsMessageBox.confirm(message, title, {
        type: "warning",
        confirmButtonText: t("ai.copilot.draft.apply"),
        cancelButtonText: t("cancel"),
    }).then(() => true).catch(() => false)
}

/** Report a failed apply, preferring the server's message over the caller's fallback. */
export async function alertError(e: unknown, fallback: string, title: string): Promise<void> {
    const err = e as {response?: {data?: {message?: string}}; message?: string}
    await KsMessageBox.alert(err?.response?.data?.message ?? err?.message ?? fallback, title, {type: "error"})
}

/** Parse an artefact's namespace + id out of its YAML; empty strings when they can't be read. */
export function parseTarget(yaml: string): {namespace: string; id: string} {
    try {
        const parsed = YAML_UTILS.parse(yaml)
        return {namespace: parsed?.namespace ?? "", id: parsed?.id ?? ""}
    } catch {
        return {namespace: "", id: ""}
    }
}

/**
 * A create failed because the artefact already exists (→ update instead). The SDK throws the
 * parsed error body with a `.response = {status, data}` attached; the "already exists" text can
 * sit at `data.message` or nested in the validation errors (`_embedded.errors[].message`), so
 * match against the whole serialized body rather than a single field.
 */
export function isAlreadyExists(e: unknown): boolean {
    const err = e as {status?: number; response?: {status?: number; data?: unknown}}
    const status = err?.response?.status ?? err?.status
    if (status !== 422) return false
    const body = err?.response?.data ?? err
    return /already exists/i.test(typeof body === "string" ? body : JSON.stringify(body ?? ""))
}
