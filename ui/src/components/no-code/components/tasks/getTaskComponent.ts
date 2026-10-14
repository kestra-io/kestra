import {pascalCase} from "change-case"
import {resolve$ref} from "../../../../utils/utils"
import {SECTIONS_IDS} from "../../utils/useFlowFields"
import {isImplementationPicker} from "./discriminatedUnion"

const TasksComponents = import.meta.glob<{ default: any }>("./Task*.vue", {eager: true})

export interface Schema{
    $ref?: string;
    $required?: boolean;
    type?: string | {const: string};
    title?: string;
    description?: string;
    markdownDescription?: string;
    properties?: Record<string, Schema>;
    required?: string[];
    default?: any;
    allOf?: Schema[];
    anyOf?: Schema[];
    oneOf?: Schema[];
    items?: Schema;
    const?: string;
    format?: string;
    enum?: unknown[];
    pattern?: string;
    $language?: string;
    $secret?: boolean;
}

export const LIST_FIELDS = SECTIONS_IDS.filter(id => id !== "outputs")

export function getType(property: any, definitions: Record<string, any>, key?: string, siblingKeys?: string[]): string {

    if (property.enum !== undefined) {
        return "enum"
    }

    if (property.$secret === true) {
        return "secret"
    }

    if (Object.prototype.hasOwnProperty.call(property, "$ref")) {
        if (property.$ref.includes("tasks.Task")) {
            return "task"
        }

        if (property.$ref.includes("tasks.runners.TaskRunner")) {
            return "task"
        }

        if (property.$ref.includes("io.kestra.preload")) {
            return "list"
        }

        if (isImplementationPicker(property, definitions)) {
            return "plugin-implementation"
        }

        return "complex"
    }

    if (Object.prototype.hasOwnProperty.call(property, "allOf")) {
        if (property.allOf.length === 2
            && property.allOf[0].$ref && !property.allOf[1].properties) {
            return "complex"
        }
    }

    if (Object.prototype.hasOwnProperty.call(property, "anyOf")) {
        if (key === "labels" && property.anyOf.length === 2
            && property.anyOf[0].type === "array" && property.anyOf[1].type === "object") {
            return "dict"
        }

        if (isImplementationPicker(property, definitions)) {
            return "plugin-implementation"
        }
        return "any-of"
    }

    if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
        return "dict"
    }

    if (property.type === "integer") {
        return "number"
    }

    if (key === "version" && property.type === "string") {
        return "version"
    }

    if (key === "namespace") {
        return "namespace"
    }

    if (key === "namespaces" && property.type === "array") {
        return "namespaces"
    }

    if (key === "tenants" && property.type === "array") {
        return "tenants"
    }

    const properties = siblingKeys ?? []
    const hasNamespaceProperty = properties.includes("namespace")
    if (key === "flowId" && hasNamespaceProperty) {
        return "subflow-id"
    }

    if (key === "dashboardId") {
        return "dashboard-id"
    }

    if (key === "chartId" && properties.includes("dashboardId")) {
        return "chart-id"
    }

    if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
        return "subflow-inputs"
    }

    if (property.type === "array") {
        const items = definitions ? resolve$ref({definitions: definitions}, property.items) : property.items
        if (LIST_FIELDS.includes(key ?? "")) {
            return "list"
        }

        if (isImplementationPicker(property, definitions)) {
            return "plugin-implementation"
        }

        // A discriminated union too large to page through in a plain TaskArray, but not
        // plugin-provided (e.g. flow Input's ~15 short-named types: string, int, json, ...) — the
        // implementation control doesn't apply, so it keeps the collapsible counted-header list.
        if (items?.anyOf?.length === 0 || items?.anyOf?.length > 10) {
            return "list"
        }

        return "array"
    }

    if (property.const) {
        return "constant"
    }

    if (property.type === "object" && !property.properties) {
        return "dict"
    }

    return property.type || "expression"
}

export function getTaskComponent(property: any, definitions: Record<string, any>, key?: string, siblingKeys?: string[]): any {
    const typeString = getType(property, definitions, key, siblingKeys)
    const type = pascalCase(typeString)
    const component = TasksComponents[`./Task${type}.vue`]?.default
    if (component) {
        component.ksTaskName = typeString
    }
    return component ?? {}
}
