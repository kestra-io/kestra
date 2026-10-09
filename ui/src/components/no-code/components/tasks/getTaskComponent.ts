import {pascalCase} from "change-case"
import type {Component} from "vue"
import {resolve$ref} from "../../../../utils/utils"
import {SECTIONS_IDS} from "../../utils/useFlowFields"
import {isImplementationPicker} from "./discriminatedUnion"

type TaskComponent = Component & {
    ksTaskName?: string
}

const TasksComponents = import.meta.glob<{ default: TaskComponent }>("./Task*.vue", {eager: true})

export interface Schema {
    $ref?: string;
    $required?: boolean;
    type?: string | {const: string};
    title?: string;
    description?: string;
    markdownDescription?: string;
    properties?: Record<string, Schema>;
    required?: string[];
    default?: unknown;
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

export function getType(property: Schema, definitions: Record<string, Schema>, key?: string, siblingKeys?: string[]): string {

    if (property.enum !== undefined) {
        return "enum"
    }

    if (property.$secret === true) {
        return "secret"
    }

    const ref = property.$ref
    if (ref) {
        if (ref.includes("tasks.Task")) {
            return "task"
        }

        if (ref.includes("tasks.runners.TaskRunner")) {
            return "task"
        }

        if (ref.includes("io.kestra.preload")) {
            return "list"
        }

        if (isImplementationPicker(property, definitions)) {
            return "plugin-implementation"
        }

        return "complex"
    }

    const allOf = property.allOf
    if (allOf && allOf.length === 2) {
        if (allOf[0].$ref && !allOf[1].properties) {
            return "complex"
        }
    }

    const anyOf = property.anyOf
    if (anyOf) {
        if (key === "labels" && anyOf.length === 2
            && anyOf[0].type === "array" && anyOf[1].type === "object") {
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
        const items = definitions && property.items
            ? resolve$ref({definitions}, property.items)
            : property.items

        if (LIST_FIELDS.includes(key ?? "")) {
            return "list"
        }

        if (isImplementationPicker(property, definitions)) {
            return "plugin-implementation"
        }

        // A discriminated union too large to page through in a plain TaskArray, but not
        // plugin-provided (e.g. flow Input's ~15 short-named types: string, int, json, ...) —
        // the implementation control doesn't apply, so it keeps the collapsible counted-header list.
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

    return typeof property.type === "string" ? property.type : "expression"
}

export function getTaskComponent(property: Schema, definitions: Record<string, Schema>, key?: string, siblingKeys?: string[]): TaskComponent | Record<string, never> {
    const typeString = getType(property, definitions, key, siblingKeys)
    const type = pascalCase(typeString)
    const component = TasksComponents[`./Task${type}.vue`]?.default

    if (component) {
        component.ksTaskName = typeString
    }

    return component ?? {}
}
