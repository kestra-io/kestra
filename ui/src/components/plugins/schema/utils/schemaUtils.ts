export interface JSONProperty {
    type: string | string[];
    name?: string;
    unit?: string;
    $dynamic?: boolean;
    $ref?: string;
    $required?: boolean;
    $beta?: boolean;
    $deprecated?: boolean;
    $internalStorageURI?: boolean;
    allOf?: JSONProperty[];
    anyOf?: JSONProperty[];
    items?: JSONProperty;
    additionalProperties?: JSONProperty;
    title?: string;
    description?: string;
    default?: string;
    pattern?: string;
    minLength?: number;
    maxLength?: number;
    minItems?: number;
    maxItems?: number;
    minimum?: number;
    exclusiveMinimum?: number;
    maximum?: number;
    exclusiveMaximum?: number;
    format?: string;
    enum?: string[];
}

export interface SchemaExample {
    title?: string;
    code: string;
    lang?: string;
    full?: boolean;
}

export interface JSONSchema {
    title?: string;
    description?: string;
    $deprecated?: boolean | string;
    definitions?: Record<string, JSONSchema>;
    $examples?: SchemaExample[];
    outputs?: {
        properties: Record<string, JSONProperty>;
    };
    properties?: Record<string, JSONProperty> & {
        title?: string;
        description?: string;
        properties?: Record<string, JSONProperty>;
        $beta?: boolean;
        $examples?: SchemaExample[];
        $metrics?: JSONProperty[];
    };
}

type ExtractedTypes = {types: string[]; subType?: string};

const FENCED_LANG_BLOCK_REGEX = /(```)(?:bash|yaml|js|console|json)(\n) *([\s\S]*?```)/g
const COLON_NORMALIZE_REGEX = /(?<!:):(?![: /])/g

const distinctFilter = <T>(value: T, index: number, array: T[]): boolean =>
    array.indexOf(value) === index

function extractTypesOrRef(property: JSONProperty): string[] | undefined {
    if (property.type) {
        return Array.isArray(property.type) ? property.type : [property.type]
    }

    if (property.$ref) {
        const key = property.$ref.split("/").pop() ?? ""
        return [`#${key}`]
    }

    return undefined
}

// Can take a "#full.class.Name" format
export function className(anchor: string): string {
    const noGenericType = anchor.split("_")[0]
    return noGenericType.substring(noGenericType.lastIndexOf(".") + 1)
}

export function extractEnumValues(property: JSONProperty): string[] | undefined {
    return property.enum
        ?? property.items?.enum
        ?? property.additionalProperties?.enum
}

export function aggregateAllOf(property: JSONProperty): JSONProperty {
    if (!property.allOf) return property

    const {allOf, ...rest} = property
    return allOf.reduce<JSONProperty>((acc, curr) => ({...acc, ...curr}), rest)
}

export function extractTypeInfo(property: JSONProperty): ExtractedTypes {
    const getTypes = (target: JSONProperty): string[] | undefined => {
        const types = extractTypesOrRef(target)
        if (types && types.length > 0) return types

        if (target.anyOf) {
            return target.anyOf
                .flatMap(extractTypesOrRef)
                .filter((t): t is string => t !== undefined)
                .filter(distinctFilter)
        }

        return undefined
    }

    const result: ExtractedTypes = {types: getTypes(property) ?? ["object"]}

    if (result.types.includes("array") && property.items) {
        const typesToAdd = getTypes(property.items)
        if (typesToAdd && property.items.anyOf) {
            result.types = result.types.filter((type) => type !== "array").concat(typesToAdd)
        }
    }

    if (property.additionalProperties) {
        result.subType = extractTypesOrRef(property.additionalProperties)?.[0]
    } else if (property.items) {
        result.subType = extractTypesOrRef(property.items)?.[0]
    }

    return result
}

export function isDeprecated(schema?: JSONSchema): boolean {
    return schema?.$deprecated === true || schema?.$deprecated === "true"
}

export function isDynamic(property: JSONProperty): boolean {
    return property.$dynamic
        ?? property.anyOf?.some((prop) => prop.$dynamic === true)
        ?? false
}

export function sanitizeForMarkdown(str: string): string {
    return str
        .replace(FENCED_LANG_BLOCK_REGEX, "$1$2$3")
        .replace(COLON_NORMALIZE_REGEX, ": ")
}
