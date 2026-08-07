// Hand-written declarations for apiMock.js, which stays plain JS so preview.jsx and
// vitest.setup.js can import it before anything else without a compile step. Needed because
// stories are type-checked with allowJs off; keep in sync with the exports of apiMock.js.

/** A payload, or a function of the request context returning one. */
type ApiRouteHandler = ((context: {body?: unknown}) => unknown) | unknown;

export function beginStoryScope(label?: string): void;
export function mockStoryApiRoutes(handlers: Record<string, ApiRouteHandler>): void;
export function resolveApiRequest(
    method: string,
    rawUrl: string,
    context?: {body?: unknown},
): {status: number; data: unknown};
export function mockClientFallback(
    method: string,
    uri: string,
    data?: unknown,
): {data: unknown; status: number; statusText: string; headers: Record<string, string>};
export let apiFetch: typeof fetch
