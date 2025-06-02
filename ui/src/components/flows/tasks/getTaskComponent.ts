import {pascalCase} from  "change-case";
import TaskAnyOf from "./TaskAnyOf.vue";
import TaskArray from "./TaskArray.vue";
import TaskComplex from "./TaskComplex.vue";
import TaskCondition from "./TaskCondition.vue";
import TaskConditions from "./TaskConditions.vue";
import TaskConstant from "./TaskConstant.vue";
import TaskDict from "./TaskDict.vue";
import TaskEnum from "./TaskEnum.vue";
import TaskExpression from "./TaskExpression.vue";
import TaskNumber from "./TaskNumber.vue";
import TaskSubflowId from "./TaskSubflowId.vue";
import TaskSubflowInputs from "./TaskSubflowInputs.vue";
import TaskSubflowNamespace from "./TaskSubflowNamespace.vue";
import TaskTask from "./TaskTask.vue";
import TaskTaskRunner from "./TaskTaskRunner.vue";
import TaskTasks from "./TaskTasks.vue";


const TasksComponentsRaw = import.meta.glob<{default: any}>("./Task*.vue", {eager: true});

export default function getTaskComponent(property: any, key?: string, schema?: any) {
    if (property.enum !== undefined) {
        return TaskEnum;
    }

    if (Object.prototype.hasOwnProperty.call(property, "$ref")) {
        if (property.$ref.includes("tasks.Task")) {
            return TaskTask
        }

        if (property.$ref.includes(".conditions.")) {
            return TaskCondition
        }

        if (property.$ref.includes("tasks.runners.TaskRunner")) {
            return TaskTaskRunner
        }

        return TaskComplex
    }

    if (Object.prototype.hasOwnProperty.call(property, "anyOf")) {
        return TaskAnyOf
    }

    if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
        return TaskDict
    }

    if (property.type === "integer") {
        return TaskNumber
    }

    if (key === "namespace") {
        return TaskSubflowNamespace
    }

    const properties = Object.keys(schema?.properties ?? {});
    const hasNamespaceProperty = properties.includes("namespace");
    if (key === "flowId" && hasNamespaceProperty) {
        return TaskSubflowId
    }

    if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
        return TaskSubflowInputs
    }

    if( property.type === "array") {
        if (property.items?.$ref?.includes("tasks.Task")) {
            return TaskTasks
        }

        if (property.items?.$ref?.includes("conditions.Condition")) {
            return TaskConditions
        }

        return TaskArray;
    }

    if (property.const) {
        return TaskConstant
    }

    return TasksComponentsRaw[`Task${pascalCase(property.type)}`].default || TaskExpression;
}