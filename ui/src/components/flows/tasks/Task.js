import YamlUtils from "../../../utils/yamlUtils";

export default {
    props: {
        modelValue: {
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
        getKey(addKey) {
            return this.root ? this.root + "." + addKey : addKey;
        },
        isRequired(key) {
            return this.schema.required && this.schema.required.includes(key);
        },
        getType(property, key) {
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

            if (Object.prototype.hasOwnProperty.call(property, "oneOf")) {
                return "one-of";
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

            const properties = Object.keys(this.schema?.properties ?? {});
            const hasNamespaceProperty = properties.includes("namespace");
            if (key === "flowId" && hasNamespaceProperty) {
                return "subflow-id";
            }

            if (key === "inputs" && hasNamespaceProperty && properties.includes("flowId")) {
                return "subflow-inputs";
            }

            return property.type || "expression";
        },
        // eslint-disable-next-line no-unused-vars
        onShow(key) {
        },

        onInput(value) {
            this.$emit("update:modelValue", value === "" || value === null || JSON.stringify(value) === "{}" ? undefined : value);
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

            return YamlUtils.stringify(this.values);
        },
        info() {
            return `${this.schema.title || this.schema.type}`
        },
        isValid() {
            return true;
        }
    }
}