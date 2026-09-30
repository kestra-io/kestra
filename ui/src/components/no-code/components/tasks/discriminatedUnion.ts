import type {Schema} from "./getTaskComponent"

export interface ImplementationBranch {
    ref: string
    definition: Schema
    branchTitle?: string
}

function refOf(branch: Schema | undefined): string | undefined {
    if (!branch) return undefined
    if (branch.$ref) return branch.$ref
    return branch.allOf?.map(refOf).find((ref) => ref !== undefined)
}

function titleOf(branch: Schema | undefined): string | undefined {
    if (!branch) return undefined
    return branch.title ?? branch.allOf?.map(titleOf).find((title) => title !== undefined)
}

function resolveDefinition(ref: string, definitions: Record<string, Schema>): Schema | undefined {
    return definitions[ref.split("/").pop() ?? ""]
}

function mergeDefinition(definition: Schema, definitions: Record<string, Schema>): Schema {
    if (!definition.allOf?.length) return definition
    return definition.allOf.reduce<Schema>((acc, part) => {
        const resolved = part.$ref ? resolveDefinition(part.$ref, definitions) : part
        if (!resolved) return acc
        return {
            ...acc,
            properties: {...acc.properties, ...resolved.properties},
            required: [...new Set([...(acc.required ?? []), ...(resolved.required ?? [])])],
        }
    }, {...definition, allOf: undefined, properties: definition.properties ?? {}, required: definition.required ?? []})
}

export interface DiscriminatedDefinition {
    properties?: {
        type?: {
            const?: string
            enum?: unknown[]
        }
    }
}

/**
 * The `const`/`enum` values a discriminated-union branch's `type` property accepts, or
 * `undefined` when the definition carries no discriminator at all. Takes the minimal structural
 * shape rather than the full {@link Schema} so `requiredFields.ts`'s own `PartialSchema` can share it.
 */
export function resolveDiscriminator(definition: DiscriminatedDefinition | undefined): string[] | undefined {
    const typeSchema = definition?.properties?.type
    if (!typeSchema) return undefined
    if (typeof typeSchema.const === "string") return [typeSchema.const]
    if (Array.isArray(typeSchema.enum) && typeSchema.enum.every((value) => typeof value === "string")) {
        return typeSchema.enum as string[]
    }
    return undefined
}

// Kestra discriminates a *plugin-extensible* union (Task, TaskRunner, and every AI-plugin
// interface) by the implementation's fully-qualified class name, since resolving it means loading
// a dynamically-installed plugin class. A closed, compile-time set baked into core — Retry
// strategies, SLA types — instead names itself with a short `@JsonSubTypes` name ("constant",
// "MAX_DURATION"): finite, never plugin-provided, and already served by TaskAnyOf's inline
// segmented switch. Requiring a dotted discriminator keeps this control scoped to the former.
const FQCN_DISCRIMINATOR = /^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)+$/

function isPluginDiscriminator(values: string[]): boolean {
    return values.length > 0 && values.every((value) => FQCN_DISCRIMINATOR.test(value))
}

function discriminatedBranch(branch: Schema, definitions: Record<string, Schema>): ImplementationBranch | undefined {
    const ref = refOf(branch)
    if (!ref) return undefined
    const rawDefinition = resolveDefinition(ref, definitions)
    if (!rawDefinition) return undefined
    const definition = mergeDefinition(rawDefinition, definitions)
    const discriminator = resolveDiscriminator(definition)
    if (!discriminator || !isPluginDiscriminator(discriminator)) return undefined
    return {ref: ref.split("/").pop() ?? ref, definition, branchTitle: titleOf(branch)}
}

function branchesOf(property: Schema): Schema[] | undefined {
    if (property.anyOf) return property.anyOf
    if (property.type === "array" && property.items?.anyOf) return property.items.anyOf
    if (property.$ref) return [property]
    return undefined
}

/**
 * Resolves a property to the implementations it can be one of, following the detection rule:
 * an `anyOf` (or `items.anyOf` for an array) of `$ref`s whose definitions all carry a discriminated
 * `type`, or a lone `$ref` whose definition does. Returns `undefined` when the property is not
 * shaped as a discriminated union at all — a single branch found means exactly one implementation.
 */
export function getImplementationBranches(property: Schema, definitions: Record<string, Schema>): ImplementationBranch[] | undefined {
    const candidates = branchesOf(property)
    if (!candidates?.length) return undefined
    const resolved = candidates.map((branch) => discriminatedBranch(branch, definitions))
    return resolved.every((branch): branch is ImplementationBranch => branch !== undefined) ? resolved : undefined
}

export function isImplementationPicker(property: Schema, definitions: Record<string, Schema>): boolean {
    return getImplementationBranches(property, definitions) !== undefined
}

export function findBranchByType(branches: ImplementationBranch[], type: unknown): ImplementationBranch | undefined {
    if (typeof type !== "string") return undefined
    return branches.find((branch) => resolveDiscriminator(branch.definition)?.includes(type))
}

export function simpleClassName(fqcn: string): string {
    return fqcn.split(".").pop() ?? fqcn
}

const CASE_BOUNDARY = /([a-z0-9])([A-Z])/g
const ACRONYM_BOUNDARY = /([A-Z]+)([A-Z][a-z])/g

function splitCaseWords(value: string): string[] {
    return value
        .replace(CASE_BOUNDARY, "$1 $2")
        .replace(ACRONYM_BOUNDARY, "$1 $2")
        .split(" ")
        .filter((word) => word.length > 0)
}

/** `GoogleGemini` -> `Google Gemini`, `KestraKVStore` -> `Kestra KV Store` — never lowercases, so acronyms survive. */
export function humanizeClassName(simpleName: string): string {
    return splitCaseWords(simpleName).join(" ")
}

/** `contentRetrievers` -> `Content retrievers` — sentence case, for a field with no shared branch title. */
export function humanizePropertyKey(key: string): string {
    const sentence = splitCaseWords(key).map((word) => word.toLowerCase()).join(" ")
    return sentence.charAt(0).toUpperCase() + sentence.slice(1)
}

/**
 * The field's label: the branch title shared by every implementation (e.g. all `provider` branches
 * carry `title: "Language model provider"`), or the humanised property key when branches disagree or
 * carry no title at all.
 */
export function resolveImplementationLabel(branches: ImplementationBranch[], fieldKey: string): string {
    // The shared label lives on the anyOf branch (every provider branch is titled "Language model
    // provider"), not on the resolved definition, whose title names the one implementation.
    return sharedTitleOf(branches, (branch) => branch.branchTitle)
        ?? sharedTitleOf(branches, (branch) => branch.definition.title)
        ?? humanizePropertyKey(fieldKey)
}

function sharedTitleOf(
    branches: ImplementationBranch[],
    pick: (branch: ImplementationBranch) => string | undefined,
): string | undefined {
    const first = pick(branches[0])
    return first && branches.every((branch) => pick(branch) === first) ? first : undefined
}

export interface ImplementationSummaryPart {
    key: string
    secret: boolean
    text?: string
}

export type ImplementationSummary =
    | {kind: "empty"}
    | {kind: "parts"; parts: ImplementationSummaryPart[]; overflow: number}

const MAX_SUMMARY_PARTS = 3

/**
 * The implementation's own explicitly-set properties, required first, skipping `type` — the raw
 * material for the card's collapsed summary line. A `$secret` property contributes the fact that it
 * is set, never its value; the caller turns each part into display text (and translates the secret
 * fact) since that's locale-dependent.
 */
// A nested implementation contributes its own name; anything else non-scalar has no useful
// one-line form, so it is left out rather than rendered as "[object Object]".
function summaryText(propertyValue: unknown): string | undefined {
    if (propertyValue === undefined || propertyValue === null || propertyValue === "") return undefined
    if (Array.isArray(propertyValue)) return undefined
    if (typeof propertyValue === "object") {
        const nestedType = (propertyValue as {type?: unknown}).type
        return typeof nestedType === "string" ? humanizeClassName(nestedType.split(".").pop() ?? nestedType) : undefined
    }
    return String(propertyValue)
}

export function summarizeImplementationValue(value: Record<string, unknown> | undefined, definition: Schema): ImplementationSummary {
    if (!value) return {kind: "empty"}

    const required = definition.required ?? []
    const entries = Object.entries(definition.properties ?? {}).filter(([key]) => key !== "type")
    entries.sort(([a], [b]) => {
        const aRequired = required.includes(a)
        const bRequired = required.includes(b)
        return aRequired === bRequired ? 0 : aRequired ? -1 : 1
    })

    const contributing = entries.filter(([key]) => summaryText(value[key]) !== undefined)
    if (!contributing.length) return {kind: "empty"}

    const parts: ImplementationSummaryPart[] = contributing.slice(0, MAX_SUMMARY_PARTS).map(([key, propertySchema]) => ({
        key,
        secret: Boolean(propertySchema.$secret),
        text: propertySchema.$secret ? undefined : summaryText(value[key]),
    }))

    return {kind: "parts", parts, overflow: Math.max(0, contributing.length - MAX_SUMMARY_PARTS)}
}
