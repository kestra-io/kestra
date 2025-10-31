<template>
    <div class="wrapper">
        <el-checkbox-button
            v-if="['duration', 'date-time'].includes(schema?.format ?? '')"
            v-model="pebble"
            :label="$t('no_code.toggle_pebble')"
            :title="$t('no_code.toggle_pebble')"
            class="ks-pebble"
        >
            <IconCodeBracesBox />
        </el-checkbox-button>

        <el-date-picker
            v-if="!pebble && schema?.format === 'date-time'"
            :modelValue="modelValue"
            type="date"
            :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root || 'date'}`"
            @update:model-value="onInput($event.toISOString())"
        />
        <el-input-number
            v-if="!pebble && showDurationDays"
            :modelValue="daysDurationValue"
            align="right"
            style="width:200px"
            :min="0"
            :controls="false"
            @update:model-value="onInputDaysDuration"
        >
            <template #suffix>
                <span class="duration-unit">{{ $t("days") }}</span>
            </template>
        </el-input-number>
        <el-time-picker
            v-if="!pebble && schema?.format === 'duration'"
            :modelValue="timeDurationValue"
            type="time"
            :defaultValue="defaultDuration"
            :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root || 'duration'}`"
            @update:model-value="onInputDuration"
            @clear="onInputDaysDuration(undefined)"
        />
        <InputText
            v-if="disabled"
            :modelValue="modelValue"
            disabled
            class="w-100 disabled-field"
        />
        <Editor
            v-else-if="pebble || !schema?.format"
            :modelValue="editorValue"
            :navbar="false"
            :fullHeight="false"
            :shouldFocus="false"
            schemaType="flow"
            :lang="`${editorLanguage}-pebble`"
            input
            @update:model-value="onInput"
            :largeSuggestions="false"
        />
    </div>
</template>
<script lang="ts" setup>
    import {ref, computed, onMounted} from "vue";
    import $moment from "moment";
    import IconCodeBracesBox from "vue-material-design-icons/CodeBracesBox.vue";
    import Editor from "../../../../components/inputs/Editor.vue";
    import InputText from "../inputs/InputText.vue";
    import {Schema} from "./getTaskComponent";

    const props = defineProps<{
        disabled?: boolean;
        modelValue?: string;
        schema?: Schema;
        root?: string;
        task?: any;
    }>();

    const emit = defineEmits<{
        (e: "update:modelValue", value: string | undefined): void;
    }>();


    const pebble = ref(false);

    // Function to detect programming language from task type
    function detectLanguageFromTaskType(): string {
        if (!props.task?.type) {
            return "plaintext";
        }

        const taskType = props.task.type;

        // Check for script tasks and extract language
        if (taskType.includes("io.kestra.plugin.scripts.")) {
            if (taskType.includes(".python.")) {
                return "python";
            } else if (taskType.includes(".node.")) {
                return "javascript";
            } else if (taskType.includes(".shell.")) {
                return "shell";
            } else if (taskType.includes(".powershell.")) {
                return "powershell";
            } else if (taskType.includes(".r.")) {
                return "r";
            } else if (taskType.includes(".julia.")) {
                return "julia";
            } else if (taskType.includes(".ruby.")) {
                return "ruby";
            } else if (taskType.includes(".go.")) {
                return "go";
            } else if (taskType.includes(".deno.")) {
                return "typescript";
            } else if (taskType.includes(".lua.")) {
                return "lua";
            } else if (taskType.includes(".bun.")) {
                return "javascript";
            } else if (taskType.includes(".php.")) {
                return "php";
            } else if (taskType.includes(".perl.")) {
                return "perl";
            } else if (taskType.includes(".groovy.")) {
                return "groovy";
            }
        }

        return "plaintext";
    }

    // Computed property for editor language
    const editorLanguage = computed(() => {
        // Only apply syntax highlighting for script and commands fields
        if (props.root === "script" || props.root === "commands" ||
            props.root?.endsWith(".script") || props.root?.endsWith(".commands")) {
            return detectLanguageFromTaskType();
        }

        return "plaintext";
    });

    const values = computed(() => {
        if (props.modelValue === undefined) {
            return props.schema?.default;
        }

        return props.modelValue;
    })

    onMounted(() => {
        const schema = props.schema;
        if (!schema) return;

        if (!["duration", "date-time"].includes(schema.format ?? "") || !props.modelValue) {
            pebble.value = false;
        } else if (schema.format === "duration" && values.value) {
            pebble.value = !$moment.duration(props.modelValue).isValid();
        } else if (schema.format === "date-time" && values.value) {
            pebble.value = isNaN(Date.parse(props.modelValue as string));
        }
    });

    // FIXME: hardcoded condition only show days input for timeWindow durations
    const showDurationDays = computed(() => {
        return props.schema?.format === "duration" && props.root?.startsWith("timeWindow")
    });

    const daysDurationValue = computed<number | undefined>(() => {
        if (typeof values.value === "string") {
            const duration = $moment.duration(values.value);
            return Math.floor(duration.asDays());
        }
        return undefined;
    });

    const timeDurationValue = computed<Date | undefined>(() => {
        if (typeof values.value === "string") {
            const duration = $moment.duration(values.value);
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
    });

    const defaultDuration = computed(() => {
        return $moment().seconds(0).minutes(0).hours(0).toDate();
    });

    function onInputDuration(value: Date | "" | null) {
        const emitted =
            value === "" || value === null
                ? undefined
                : $moment
                    .duration({
                        days: daysDurationValue.value || 0,
                        seconds: value.getSeconds(),
                        minutes: value.getMinutes(),
                        hours: value.getHours(),
                    })
                    .toString();
        emit("update:modelValue", emitted);
    }

    function onInputDaysDuration(value: number | undefined) {
        const currentTimeDuration = timeDurationValue.value;
        const emitted = (value === undefined)
            ? undefined
            : currentTimeDuration === undefined
                ? $moment
                    .duration({
                        days: value,
                    })
                    .toString()
                : $moment
                    .duration({
                        days: value,
                        hours: currentTimeDuration.getHours(),
                        minutes: currentTimeDuration.getMinutes(),
                        seconds: currentTimeDuration.getSeconds(),
                    })
                    .toString()
        emit("update:modelValue", emitted);
    }

    function onInput(value: string) {
        emit("update:modelValue", value);
    }

    const editorValue = computed(() => props.modelValue);

</script>

<style scoped lang="scss">
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

.duration-unit{
    color: var(--ks-content-inactive);
    font-size: 0.875rem;
    line-height: 1.25rem;
    background-color: transparent;
}

</style>
