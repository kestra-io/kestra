import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import {defineComponent} from "vue";

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
            default: undefined
        }
    },
    emits: ["update:modelValue"],
    methods: {
        getKey(addKey: string) {
            return this.root ? this.root + "." + addKey : addKey;
        },
        isRequired(key: string) {
            return this.schema?.required?.includes(key);
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
            return this.schema?.title ?? this.schema?.type
        },
        isValid() {
            return true;
        }
    }
})