import { durationUtils } from "@kestra-io/design-system";

export const HTTP_DOWNLOAD_TYPE = "io.kestra.plugin.core.http.Download";

export const BODY_PREVIEW_LIMIT = 4000;
const REDACTED = "***";

export type HttpTaskKind = "request" | "download";

export interface HttpHeaderRow {
    name: string;
    value: string;
    redacted: boolean;
}

export interface HttpRedirect {
    code?: number;
    location: string;
}

export interface HttpRequestInfo {
    method?: string;
    uri?: string;
    host?: string;
    path?: string;
    queryParamNames: string[];
    headerNames: string[];
    formFieldNames: string[];
    contentType?: string;
    bodyBytes?: number;
    hasBody?: boolean;
    auth?: string;
    timeout?: string;
    followRedirects?: boolean;
}

export type HttpBody = { kind: "encrypted" } | { kind: "text"; text: string; truncated: boolean };

export interface HttpSummary {
    kind: HttpTaskKind;
    request?: HttpRequestInfo;
    method?: string;
    host?: string;
    path?: string;
    code?: number;
    reason?: string;
    contentType?: string;
    bytes?: number;
    durationMs?: number;
    finalUri?: string;
    protocol?: string;
    retries?: number;
    redirects: HttpRedirect[];
    headers: HttpHeaderRow[];
    body?: HttpBody;
    fileUri?: string;
}

type Dict = Record<string, unknown>;

const isDict = (value: unknown): value is Dict =>
    typeof value === "object" && value !== null && !Array.isArray(value);

const asString = (value: unknown): string | undefined =>
    typeof value === "string" && value !== "" ? value : undefined;

const asNumber = (value: unknown): number | undefined =>
    typeof value === "number" && Number.isFinite(value) ? value : undefined;

const asBoolean = (value: unknown): boolean | undefined =>
    typeof value === "boolean" ? value : undefined;

const asStrings = (value: unknown): string[] =>
    Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];

export const httpTaskKind = (type: string): HttpTaskKind =>
    type === HTTP_DOWNLOAD_TYPE ? "download" : "request";

function hostAndPath(uri: string | undefined): { host?: string; path?: string } {
    if (!uri) return {};
    try {
        const url = new URL(uri);
        return { host: url.host || undefined, path: url.pathname };
    } catch {
        return {};
    }
}

function parseRequest(raw: unknown): HttpRequestInfo | undefined {
    if (!isDict(raw)) return undefined;
    return {
        method: asString(raw.method),
        uri: asString(raw.uri),
        host: asString(raw.host),
        path: asString(raw.path),
        queryParamNames: asStrings(raw.queryParamNames),
        headerNames: asStrings(raw.headerNames),
        formFieldNames: asStrings(raw.formFieldNames),
        contentType: asString(raw.contentType),
        bodyBytes: asNumber(raw.bodyBytes),
        hasBody: asBoolean(raw.hasBody),
        auth: asString(raw.auth),
        timeout: asString(raw.timeout),
        followRedirects: asBoolean(raw.followRedirects),
    };
}

function parseHeaders(raw: unknown): HttpHeaderRow[] {
    if (!isDict(raw)) return [];
    return Object.entries(raw).map(([name, values]) => {
        const list = Array.isArray(values) ? values.map(String) : [String(values)];
        return { name, value: list.join(", "), redacted: list.includes(REDACTED) };
    });
}

function parseRedirects(raw: unknown): HttpRedirect[] {
    if (!Array.isArray(raw)) return [];
    return raw.filter(isDict).flatMap((redirect) => {
        const location = asString(redirect.location);
        return location ? [{ code: asNumber(redirect.code), location }] : [];
    });
}

function parseBody(outputs: Dict): HttpBody | undefined {
    // An encrypted body must never be rendered, not even partly.
    if (outputs.encryptedBody != null) return { kind: "encrypted" };
    const body = outputs.body;
    if (body == null || body === "") return undefined;
    const text = typeof body === "string" ? body : JSON.stringify(body, null, 2);
    return text.length > BODY_PREVIEW_LIMIT
        ? { kind: "text", text: text.slice(0, BODY_PREVIEW_LIMIT), truncated: true }
        : { kind: "text", text, truncated: false };
}

/**
 * Reads the outputs of an HTTP task run. Returns undefined when there is nothing to show,
 * which covers a task that has not run yet.
 */
export function parseHttpOutputs(
    outputs: Record<string, unknown> | undefined,
    kind: HttpTaskKind,
): HttpSummary | undefined {
    if (!outputs || Object.keys(outputs).length === 0) return undefined;

    const request = parseRequest(outputs.request);
    // The Request task exposes the redacted request URI as `uri`, Download exposes a storage URI there instead.
    const requestUri = request?.uri ?? (kind === "request" ? asString(outputs.uri) : undefined);
    const finalUri = asString(outputs.finalUri);
    const fallback = hostAndPath(finalUri ?? requestUri);

    return {
        kind,
        request,
        method: request?.method,
        host: request?.host ?? fallback.host,
        path: request?.path ?? fallback.path,
        code: asNumber(outputs.code),
        reason: asString(outputs.reason),
        contentType: asString(outputs.contentType),
        bytes: asNumber(outputs.contentLength) ?? asNumber(outputs.length),
        durationMs: asNumber(outputs.durationMs),
        finalUri,
        protocol: asString(outputs.protocol),
        retries: asNumber(outputs.retries),
        redirects: parseRedirects(outputs.redirects),
        headers: parseHeaders(outputs.headers),
        body: kind === "request" ? parseBody(outputs) : undefined,
        fileUri: kind === "download" ? asString(outputs.uri) : undefined,
    };
}

export function formatTimeout(timeout: string): string {
    try {
        return durationUtils.humanDuration(timeout);
    } catch {
        return timeout;
    }
}

export function formatMillis(ms: number): string {
    return durationUtils.humanDuration(ms / 1000);
}

export function formatBytes(bytes: number): string {
    if (Math.abs(bytes) < 1024) return `${bytes} B`;
    const units = ["KiB", "MiB", "GiB", "TiB"];
    let value = bytes;
    let unit = -1;
    do {
        value /= 1024;
        unit++;
    } while (Math.abs(value) >= 1024 && unit < units.length - 1);
    return `${value.toFixed(1)} ${units[unit]}`;
}

export function formatStatus(code?: number, reason?: string): string | undefined {
    return code === undefined ? undefined : [code, reason].filter(Boolean).join(" ");
}

// The host mounts the artifact in its own window, so its base path is read from there.
export function fileName(uri: string): string {
    return uri.split("/").filter(Boolean).at(-1) ?? uri;
}

const GENERATED_TEMP_NAME = /^\d+\.tmp$/;

// A saveAs or Content-Disposition name is already the storage name, only a generated temp name is worth replacing.
export function downloadFileName(summary: Pick<HttpSummary, "fileUri" | "path" | "finalUri">): string | undefined {
    const stored = summary.fileUri ? fileName(summary.fileUri) : undefined;
    if (stored && !GENERATED_TEMP_NAME.test(stored)) return stored;

    const segment = (summary.path ?? hostAndPath(summary.finalUri).path)?.split("/").filter(Boolean).at(-1);
    if (segment) {
        try {
            return decodeURIComponent(segment);
        } catch {
            return segment;
        }
    }
    return stored;
}

export function executionFileUrl(
    executionId: string | undefined,
    path: string,
    tenant?: string,
): string {
    const root = (window as unknown as { KESTRA_BASE_PATH?: string }).KESTRA_BASE_PATH?.trim();
    const base = (root || window.location.origin).replace(/\/$/, "");
    return `${base}/api/v1/${tenant ?? "main"}/executions/${executionId}/file?path=${encodeURIComponent(path)}`;
}