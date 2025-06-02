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
        TaskEnum.name = "enum"
        return TaskEnum;
    }

    if (Object.prototype.hasOwnProperty.call(property, "$ref")) {
        if (property.$ref.includes("tasks.Task")) {
            TaskTask.name = "task"
            return TaskTask
        }

        if (property.$ref.includes(".conditions.")) {
            TaskCondition.name = "condition"
            return TaskCondition
        }

        if (property.$ref.includes("tasks.runners.TaskRunner")) {
            TaskTaskRunner.name = "task-runner"
            return TaskTaskRunner
        }

        TaskComplex.name = "complex"
        return TaskComplex
    }

    if (Object.prototype.hasOwnProperty.call(property, "anyOf")) {
        return TaskAnyOf
    }

    if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
        TaskDict.name = "dict"
        return TaskDict
    }

    if (property.type === "integer") {
        TaskNumber.name = "number"
        return TaskNumber
    }

    if (key === "namespace") {
        TaskSubflowNamespace.name = "namespace"
        return TaskSubflowNamespace
    }

    const properties = Object.keys(schema?.properties ?? {});
    const hasNamespaceProperty = properties.includes("namespace");
    if (key === "flowId" && hasNamespaceProperty) {
        TaskSubflowId.name = "subflow-id"
        return TaskSubflowId
    }

    if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
        TaskSubflowInputs.name = "subflow-inputs"
        return TaskSubflowInputs
    }

    if( property.type === "array") {
        if (property.items?.$ref?.includes("tasks.Task")) {
            TaskTasks.name = "tasks"
            return TaskTasks
        }

        if (property.items?.$ref?.includes("conditions.Condition")) {
            TaskConditions.name = "conditions"
            return TaskConditions
        }

        TaskArray.name = "array"
        return TaskArray;
    }

    if (property.const) {
        TaskConstant.name = "constant"
        return TaskConstant
    }

    const Comp = TasksComponentsRaw[`./Task${pascalCase(property.type)}.vue`]?.default
    if (Comp) {
        Comp.name = property.type;
        return Comp;
    }

    return TaskExpression;
}