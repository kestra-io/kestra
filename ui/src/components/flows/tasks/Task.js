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
    created() {
        // if (this.schema.default && this.modelValue === undefined) {
        //     this.$emit("update:modelValue", this.schema.default);
        // }
    },
    methods: {
        getKey(property) {
            return this.root ? this.root + "." + property : property;
        },
        isRequired(key) {
            return this.schema.required && this.schema.required.includes(key);
        },
        getType(property) {
            if (property.enum !== undefined) {
                return "enum";
            }

            if (Object.prototype.hasOwnProperty.call(property, "$ref")) {
                if (property.$ref.includes("Task")) {
                    return "task"
                }
            }

            if (Object.prototype.hasOwnProperty.call(property, "additionalProperties")) {
                return "dict";
            }

            if (property.type === "integer") {
                return "number";
            }

            if (property.type === "object" && property.properties && Object.keys(property.properties).length >= 1) {
                return "complex";
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