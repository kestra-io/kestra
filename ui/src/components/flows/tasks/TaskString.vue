<template>
    <div class="wrapper">
        <el-checkbox-button
            v-if="['duration', 'date-time'].includes(schema.format)"
            v-model="pebble"
            :label="$t('no_code.toggle_pebble')"
            :title="$t('no_code.toggle_pebble')"
            class="ks-pebble"
        >
            <IconCodeBracesBox />
        </el-checkbox-button>
        <el-time-picker
            v-if="!pebble && schema.format === 'duration'"
            :model-value="durationValue"
            type="time"
            :default-value="defaultDuration"
            :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root || 'duration'}`"
            @update:model-value="onInputDuration"
        />
        <el-date-picker
            v-else-if="!pebble && schema.format === 'date-time'"
            :model-value="modelValue"
            type="date"
            :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root || 'date'}`"
            @update:model-value="onInput($event.toISOString())"
        />
        <InputText
            v-else-if="disabled"
            :model-value="modelValue"
            disabled
            class="w-100 disabled-field"
        />
        <editor
            v-else
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
    </div>
</template>
<script setup>
    import Editor from "../../../components/inputs/Editor.vue";
    import InputText from "../../code/components/inputs/InputText.vue";
    import IconCodeBracesBox from "vue-material-design-icons/CodeBracesBox.vue";
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
        data() {
            return {
                pebble: false,
            };
        },
        emits: ["update:modelValue"],
        mounted(){
            if(!["duration", "date-time"].includes(this.schema.format) || !this.modelValue){
                this.pebble = false;
            } else if( this.schema.format === "duration" && this.values) {
                this.pebble = !this.$moment.duration(this.modelValue).isValid();
            } else if (this.schema.format === "date-time" && this.values) {
                this.pebble = isNaN(Date.parse(this.modelValue));
            }
        },
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

.wrapper {
    display: flex;
    align-items: stretch;
    justify-content: stretch;
    border-radius: 0.25rem;
    border: 1px solid var(--ks-border-primary);
    width: 100%;

    :deep(.disabled-field) {
        margin: 0!important;
        border-radius: 4px;
    }

    :deep(.el-input__wrapper),
    :deep(.editor-container) {
        box-shadow: none;
    }

    :deep(.el-checkbox-button__inner) {
        padding: 4px;
        border: none;
    }

    .ks-pebble:deep(span:hover){
        color: var(--ks-content-link-hover) ;
    }

    .ks-pebble * {
        font-size: 24px;
        vertical-align: top;
    }
}

</style>
