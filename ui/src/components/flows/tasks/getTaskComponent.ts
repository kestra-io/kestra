import {pascalCase} from  "change-case";
import InputPair from "../../code/components/inputs/InputPair.vue";

const TasksComponents = import.meta.glob<{default: any}>("./Task*.vue", {eager: true});

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

        return "complex";
    }

    if (Object.prototype.hasOwnProperty.call(property, "anyOf")) {
        return "any-of";
    }

    if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
        return "dict";
    }

    if (property.type === "integer") {
        return "number";
    }

    if (key === "namespace") {
        return "subflow-namespace";
    }

    const properties = Object.keys(schema?.properties ?? {});
    const hasNamespaceProperty = properties.includes("namespace");
    if (key === "flowId" && hasNamespaceProperty) {
        return "subflow-id";
    }

    if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
        return "subflow-inputs";
    }

    if( property.type === "array") {
        if (property.items?.$ref?.includes("tasks.Task")) {
            return "tasks";
        }

        if (property.items?.$ref?.includes("conditions.Condition")) {
            return "conditions";
        }

        return "array";
    }

    if (property.const) {
        return "constant"
    }

    if( property.type === "object" && !property.properties) {
        return "input-pair";
    }

    return property.type || "expression";
}

export default function getTaskComponent(property: any, key?: string, schema?: any) {
    const typeString = getType(property, key, schema);
    if( typeString === "input-pair") {
        return InputPair;
    }
    const type = pascalCase(typeString);
    const component = TasksComponents[`./Task${type}.vue`]?.default;
    if (component) {
        component.ksTaskName = typeString;
    }
    return component
}