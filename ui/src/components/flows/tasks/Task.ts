import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import {defineComponent} from "vue";

export function getType(property: any, key?: string, schema?: any): string {
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

    return property.type || "expression";
}

export function collapseEmptyValues(value: any): any {
    return value === "" || value === null || JSON.stringify(value) === "{}" ? undefined : value
}

export default defineComponent({
    props: {
        modelValue: {
            type: [Object, String, Number, Boolean, Array],
            default: undefined
        },
        schema: {
            type: Object,
            default: undefined
        },
        required: {
            type: Boolean,
            default: false
        },
        task: {
            type: Object,
            default: undefined
        },
        root: {
            type: String,
            default: undefined
        },
        definitions: {
            type: Object,
            default: () => undefined
        }
    },
    emits: ["update:modelValue"],
    methods: {
        getKey(addKey: string) {
            return this.root ? this.root + "." + addKey : addKey;
        },
        getType(property:any, key: string) {
            return getType(property, key, this.schema);
        },
        isRequired(key: string) {
            return this.schema?.required?.includes(key);
        },
        onShow() {
        },

        onInput(value:any) {
            this.$emit("update:modelValue", collapseEmptyValues(value));
        }
    },
    computed: {
        values() {
            if (this.modelValue === undefined) {
                return this.schema?.default;
            }

            return this.modelValue;
        },
        editorValue() {
            if (typeof this.values === "string") {
                return this.values;
            }

            return YAML_UTILS.stringify(this.values);
        },
        info() {
            return `${this.schema?.title || this.schema?.type}`
        },
        isValid() {
            return true;
        }
    }
})