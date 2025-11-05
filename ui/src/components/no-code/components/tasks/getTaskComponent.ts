import {pascalCase} from "change-case";
import {resolve$ref} from "../../../../utils/utils";

const TasksComponents = import.meta.glob<{ default: any }>("./Task*.vue", {eager: true});

export interface Schema{
    $ref?: string;
    $required?: boolean;
    type: string | {const: string};
    properties?: Record<string, Schema>;
    required?: string[];
    default?: any;
    allOf?: Schema[];
    anyOf?: Schema[];
    oneOf?: Schema[];
    items?: Schema;
    const?: string;
    format?: string;
}

function getType(property: any, definitions: Record<string, any>, key?: string): string {
    
    if (property.enum !== undefined) {
        return "enum";
    }

    if (Object.prototype.hasOwnProperty.call(property, "$ref")) {
        if (property.$ref.includes("tasks.Task")) {
            return "task"
        }

        if (property.$ref.includes("tasks.runners.TaskRunner")) {
            return "task-runner"
        }

        if (property.$ref.includes("io.kestra.preload")) {
            return "list"
        }

        return "complex";
    }

    if (Object.prototype.hasOwnProperty.call(property, "allOf")) {
        if (property.allOf.length === 2
            && property.allOf[0].$ref && !property.allOf[1].properties) {
            return "complex";
        }
    }

    if (Object.prototype.hasOwnProperty.call(property, "anyOf")) {
        if (key === "labels" && property.anyOf.length === 2
            && property.anyOf[0].type === "array" && property.anyOf[1].type === "object") {
            return "dict";
        }

        // for dag tasks
        if (property.anyOf.length > 10) {
            return "task"
        }
        return "any-of";
    }

    if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
        return "dict";
    }

    if (property.type === "integer") {
        return "number";
    }

    if (key === "version" && property.type === "string") {
        return "version";
    }

    if (key === "namespace") {
        return "namespace";
    }

    const properties = Object.keys(definitions?.properties ?? {});
    const hasNamespaceProperty = properties.includes("namespace");
    if (key === "flowId" && hasNamespaceProperty) {
        return "subflow-id";
    }

    if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
        return "subflow-inputs";
    }

    if (property.type === "array") {
        const items = definitions ? resolve$ref({definitions: definitions}, property.items) : property.items;
        if (items?.anyOf?.length === 0 || items?.anyOf?.length > 10 || key === "pluginDefaults" || key === "layout") {
            return "list";
        }

        return "array";
    }

    if (property.const) {
        return "constant"
    }

    if (property.type === "object" && !property.properties) {
        return "dict";
    }

    return property.type || "expression";
}

export function getTaskComponent(property: any, definitions: Record<string, any>, key?: string): any {
    const typeString = getType(property, definitions, key);
    const type = pascalCase(typeString);
    const component = TasksComponents[`./Task${type}.vue`]?.default;
    if (component) {
        component.ksTaskName = typeString;
    }
    return component ?? {}
}
