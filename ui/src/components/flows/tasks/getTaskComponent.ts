import {pascalCase} from "change-case";

const TasksComponents = import.meta.glob<{ default: any }>("./Task*.vue", {eager: true});

function getType(property: any, key?: string, schema?: any): string {
    if (property.enum !== undefined) {
        return "enum";
    }

    if (Object.prototype.hasOwnProperty.call(property, "$ref")) {
        if (property.$ref.includes("tasks.Task")) {
            return "task"
        }

        if (property.$ref.includes(".conditions.")) {
            return "condition"
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
            return "KV-pairs";
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

    const properties = Object.keys(schema?.properties ?? {});
    const hasNamespaceProperty = properties.includes("namespace");
    if (key === "flowId" && hasNamespaceProperty) {
        return "subflow-id";
    }

    if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
        return "subflow-inputs";
    }

    if (property.type === "array") {
        if (property.items?.anyOf?.length === 0 || property.items?.anyOf?.length > 10 || key === "pluginDefaults" || key === "layout") {
            return "list";
        }

        return "array";
    }

    if (property.const) {
        return "constant"
    }

    if (property.type === "object" && !property.properties) {
        return "KV-pairs";
    }

    return property.type || "expression";
}

export default function getTaskComponent(property: any, key?: string, schema?: any) {
    const typeString = getType(property, key, schema);
    const type = pascalCase(typeString);
    const component = TasksComponents[`./Task${type}.vue`]?.default;
    if (component) {
        component.ksTaskName = typeString;
    }
    return component ?? {}
}