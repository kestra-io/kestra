/**
 * hey-api prefers a declared `application/json` request-body variant, mislabeling raw YAML source bodies (#340).
 * For string-schema YAML bodies, drop the JSON variant and put `application/x-yaml` first so the parser picks it.
 */
const YAML_MEDIA_TYPE = "application/x-yaml"
const JSON_MEDIA_TYPE = "application/json"

function isPlainString(schema: unknown): boolean {
    const record = asRecord(schema)
    return record?.type === "string" && record.format !== "binary"
}

export function fixYamlSourceRequestBodyContentType(method: string, path: string, operation: Operation): void {
    const requestBody = "requestBody" in operation ? asRecord(operation.requestBody) : undefined
    if (!requestBody || "$ref" in requestBody) return
    const content = asRecord(requestBody.content)
    if (!content || typeof content !== "object") return

    const yamlContent = asRecord(content[YAML_MEDIA_TYPE])
    const jsonContent = asRecord(content[JSON_MEDIA_TYPE])
    if (!isPlainString(yamlContent?.schema)) return
    if (!isPlainString(jsonContent?.schema)) return

    delete content[JSON_MEDIA_TYPE]

    const reordered: SchemaRecord = {[YAML_MEDIA_TYPE]: yamlContent}
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
export function normalizeQueryFilterParams(method: string, path: string, operation: Operation): void {
    const parameters = "parameters" in operation ? operation.parameters : undefined
    if (!Array.isArray(parameters)) return

    for (const param of parameters) {
        const parameter = asRecord(param)
        if (!parameter || parameter.in !== "query") continue
        const schema = asRecord(parameter.schema)
        if (!schema || schema.type !== "array") continue
        const items = asRecord(schema.items)
        if (typeof items?.$ref !== "string" || !items.$ref.endsWith("/QueryFilter")) continue

        if (parameter.required === true && !schema.nullable) {
            delete parameter.required
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
export function widenQueryFilterValue(schema: Schema): void {
    const properties = asRecord(schema.properties)
    if (properties?.value) {
        properties.value = {}
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
export function replaceFlowLabels(schema: Schema): void {
    const schemaRecord = asRecord(schema)
    if (!schemaRecord) return

    const labelsAsArray = (): SchemaRecord => ({type: "array", items: {$ref: "#/components/schemas/Label"}})

    const properties = asRecord(schemaRecord.properties)
    if (properties?.labels) {
        properties.labels = labelsAsArray()
    }
    for (const composition of ["allOf", "anyOf", "oneOf"] as const) {
        const parts = schemaRecord[composition]
        if (!Array.isArray(parts)) continue
        for (const part of parts) {
            const partProperties = asRecord(asRecord(part)?.properties)
            if (partProperties?.labels) {
                partProperties.labels = labelsAsArray()
            }
        }
    }
}
import type {OpenApiOperationObject, OpenApiSchemaObject} from "@hey-api/openapi-ts"

type Operation = OpenApiOperationObject.V2_0_X | OpenApiOperationObject.V3_0_X | OpenApiOperationObject.V3_1_X
type Schema = OpenApiSchemaObject.V2_0_X | OpenApiSchemaObject.V3_0_X | OpenApiSchemaObject.V3_1_X
type SchemaRecord = Record<string, unknown>

function asRecord(value: unknown): SchemaRecord | undefined {
    return value !== null && typeof value === "object" ? value as SchemaRecord : undefined
}
