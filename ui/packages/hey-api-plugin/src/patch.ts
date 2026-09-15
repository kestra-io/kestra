/**
 * hey-api prefers a declared `application/json` request-body variant, mislabeling raw YAML source bodies (#340).
 * For string-schema YAML bodies, drop the JSON variant and put `application/x-yaml` first so the parser picks it.
 */
const YAML_MEDIA_TYPE = "application/x-yaml"
const JSON_MEDIA_TYPE = "application/json"
const EVENT_STREAM_MEDIA_TYPE = "text/event-stream"

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null
}

function isPlainString(schema: any): boolean {
    return !!schema && schema.type === "string" && schema.format !== "binary"
}

export function fixYamlSourceRequestBodyContentType(_method: string, _path: string, operation: any): void {
    const requestBody = operation?.requestBody
    const content = requestBody?.content
    if (!content || typeof content !== "object") return

    if (!isPlainString(content[YAML_MEDIA_TYPE]?.schema)) return
    if (!isPlainString(content[JSON_MEDIA_TYPE]?.schema)) return

    delete content[JSON_MEDIA_TYPE]

    const reordered: Record<string, unknown> = {[YAML_MEDIA_TYPE]: content[YAML_MEDIA_TYPE]}
    for (const [mediaType, value] of Object.entries(content)) {
        if (mediaType !== YAML_MEDIA_TYPE) reordered[mediaType] = value
    }
    requestBody.content = reordered

}

/**
 * Make required `filters` (QueryFilter[]) query parameters optional.
 *
 * When a `filters` array query parameter is `required: true` and non-nullable, hey-api emits a
 * broken `querySerializer: { array: { explode: false } }` that stringifies each QueryFilter to
 * "[object Object]"; endpoints whose schema is already `nullable` serialize correctly. Dropping
 * `required` + adding `nullable` makes every filter parameter behave like the working ones — and,
 * as a welcome side effect, lets callers pass a filter bag without an explicit (often empty)
 * `filters` array. Mirrors the client-sdk customizer's `normalizeQueryFilterParams`; applied here in
 * the shared plugin so the OSS/EE SDKs match the published client-sdk without a separate sanitizer.
 *
 * Use as a `parser.patch.operations` hook (signature `(method, path, operation)`).
 */
export function normalizeQueryFilterParams(_method: string, _path: string, operation: any): void {
    const parameters = operation?.parameters
    if (!Array.isArray(parameters)) return

    for (const param of parameters) {
        if (!param || typeof param !== "object" || param.in !== "query") continue
        const schema = param.schema
        if (!schema || schema.type !== "array") continue
        if (typeof schema.items?.$ref !== "string" || !schema.items.$ref.endsWith("/QueryFilter")) continue

        if (param.required === true && !schema.nullable) {
            delete param.required
            schema.nullable = true
        }
    }
}

/**
 * Widen `QueryFilter.value` from `type: object` (which generators turn into `{ [key: string]:
 * unknown }`) to an empty schema, so it maps to `unknown`. The value carries strings, numbers,
 * booleans, or arrays depending on the operator, so `unknown` is the accurate shape and lets callers
 * assign a scalar/array directly. Mirrors the client-sdk customizer's `widenQueryFilterValue`.
 *
 * Use as a `parser.patch.schemas` hook keyed by `QueryFilter` (signature `(schema)`).
 */
export function widenQueryFilterValue(schema: any): void {
    if (schema?.properties?.value) {
        schema.properties.value = {}
    }
}

/**
 * Replace a flow-like schema's `labels` property with an array of `Label` refs.
 *
 * The backend serializes `labels` as a map (`{ [key]: object }` → `MapObjectObject`) in the raw
 * spec, but the UI (and the client-sdk consumers) treat labels as a `Label[]`. Mirrors the client-sdk
 * customizer's `replaceFlowLabelsSpec`. Handles the property both directly and inside allOf/anyOf/oneOf
 * composition blocks.
 *
 * Use as a `parser.patch.schemas` hook keyed by `Flow` / `AbstractFlow` / `FlowWithSource`
 * (signature `(schema)`).
 */
export function replaceFlowLabels(schema: any): void {
    if (!schema || typeof schema !== "object") return

    const labelsAsArray = () => ({type: "array", items: {$ref: "#/components/schemas/Label"}})

    if (schema.properties?.labels) {
        schema.properties.labels = labelsAsArray()
    }
    for (const composition of ["allOf", "anyOf", "oneOf"] as const) {
        if (Array.isArray(schema[composition])) {
            for (const part of schema[composition]) {
                if (part?.properties?.labels) {
                    part.properties.labels = labelsAsArray()
                }
            }
        }
    }
}

/**
 * Unwrap a `text/event-stream` response declared as an array of events to the event schema itself.
 *
 * Since Micronaut 4.10.18 the OpenAPI spec renders a `Publisher<Event<T>>` endpoint as an `array`
 * of the event where it used to declare the event alone. hey-api's SSE client types each event its
 * stream yields from the response schema: an object schema yields the union of its property types
 * (the `data` payload among them), anything else is yielded as declared - so the array typed every
 * yielded event as `EventT[]`, a shape the client never produces at runtime (it parses one event at
 * a time). Keeping the item schema keeps the generated types the shape the client yields, and the
 * shape they had before the upgrade.
 *
 * Use as a `parser.patch.operations` hook (signature `(method, path, operation)`).
 */
export function unwrapEventStreamArrayResponses(_method: string, _path: string, operation: unknown): void {
    if (!isRecord(operation) || !isRecord(operation.responses)) return

    for (const response of Object.values(operation.responses)) {
        if (!isRecord(response) || !isRecord(response.content)) continue
        const media = response.content[EVENT_STREAM_MEDIA_TYPE]
        if (!isRecord(media) || !isRecord(media.schema)) continue
        const schema = media.schema
        if (schema.type === "array" && isRecord(schema.items)) {
            media.schema = schema.items
        }
    }
}
