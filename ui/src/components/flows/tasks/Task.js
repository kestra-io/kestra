export default {
    props: {
        modelValue: {
            default: undefined
        },
        schema: {
            type: Object,
            required: true
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

                return "complex";
            }

            if (Object.prototype.hasOwnProperty.call(property, "oneOf")) {
                return "oneOf";
            }

            if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
                return "dict";
            }

            if (property.type === "integer") {
                return "number";
            }

            if (key === "namespace" || key === "flowId") {
                return key;
            }

            return property.type || "dynamic";
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
                return this.schema.default;
            }

            return this.modelValue;
        },
        editorValue() {
            return this.values ? this.values : "";
        },
        info() {
            return `${this.schema.title || this.schema.type}`
        },
        isValid() {
            return true;
        }
    }
}