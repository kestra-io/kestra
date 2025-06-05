<template>
    <template v-if="schema.format === 'duration'">
        <el-time-picker
            :model-value="durationValue"
            type="time"
            :default-value="defaultDuration"
            :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root || 'duration'}`"
            @update:model-value="onInputDuration"
        />
    </template>
    <template v-else>
        <editor
            v-if="!disabled"
            :model-value="editorValue"
            :navbar="false"
            :full-height="false"
            :should-focus="false"
            schema-type="flow"
            lang="plaintext"
            input
            :placeholder="`Your ${root || 'value'} here...`"
            @update:model-value="onInput"
            :large-suggestions="false"
        />
        <InputText
            v-else
            :model-value="modelValue"
            disabled
            class="w-100"
        />
    </template>
</template>
<script setup>
    import Editor from "../../../components/inputs/Editor.vue";
    import InputText from "../../code/components/inputs/InputText.vue";

</script>
<script>
    import Task from "./Task";

    export default {
        inheritAttrs: false,
        mixins: [Task],
        components: {Editor},
        props:{
            disabled: {
                type: Boolean,
                default: false,
            },
        },
        emits: ["update:modelValue"],
        computed: {
            isValid() {
                if (this.required && !this.modelValue) {
                    return false;
                }

                if (this.schema.regex && this.modelValue) {
                    return RegExp(this.schema.regex).test(this.modelValue);
                }

                return true;
            },
            durationValue() {
                if (typeof this.values === "string") {
                    const duration = this.$moment.duration(this.values);

                    return new Date(
                        1981,
                        1,
                        1,
                        duration.hours(),
                        duration.minutes(),
                        duration.seconds(),
                    );
                }

                return undefined;
            },
            defaultDuration() {
                return this.$moment().seconds(0).minutes(0).hours(0).toDate();
            },
        },
        methods: {
            onInputDuration(value) {
                const emitted =
                    value === "" || value === null
                        ? undefined
                        : this.$moment
                            .duration({
                                seconds: value.getSeconds(),
                                minutes: value.getMinutes(),
                                hours: value.getHours(),
                            })
                            .toString();

                this.$emit("update:modelValue", emitted);
            },
            onInput(value) {
                this.$emit("update:modelValue", value);
            },
        },
    };
</script>

<style lang="scss" scoped>
:deep(.el-input__inner) {
    &::placeholder {
        color: var(--ks-content-inactive) !important;
    }
}
:deep(.placeholder) {
    top: -7px !important;
}
</style>
