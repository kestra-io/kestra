<template>
    <el-tooltip :disabled="tooltip === undefined" :content="tooltip" effect="light">
        <el-select
            data-test-id="time-selector"
            :modelValue="value"
            :placeholder="placeholder"
            @change="emit('change', $event)"
            :clearable="clearable"
        >
            <template #prefix>
                <ClockOutline />
            </template>
            <el-option
                v-for="preset in options"
                :key="preset.value"
                :label="$t(preset.label)"
                :value="preset.value"
            />
        </el-select>
    </el-tooltip>
</template>

<script lang="ts" setup>
    import {PropType} from "vue";
    import ClockOutline from "vue-material-design-icons/ClockOutline.vue";

    const emit = defineEmits<{
        (e: "change", value: string | undefined): void;
    }>();

    defineProps({
        placeholder: {
            type: String,
            default: undefined
        },
        value: {
            type: String,
            default: undefined
        },
        options: {
            type: Array as PropType<
                {
                    value?: string;
                    label: string;
                }[]
            >,
            default: () => []
        },
        tooltip: {
            type: String,
            default: undefined
        },
        clearable: {
            type: Boolean,
            default: false
        }
    })
</script>