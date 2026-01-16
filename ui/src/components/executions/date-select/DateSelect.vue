<template>
    <el-tooltip :disabled="tooltip === undefined" :content="tooltip" effect="light">
        <el-select
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

<script setup lang="ts">
    import ClockOutline from "vue-material-design-icons/ClockOutline.vue";

    interface Option {
        value: string;
        label: string;
    }

    defineProps<{
        placeholder?: string,
        value?: string,
        options: Option[],
        tooltip?: string,
        clearable?: boolean
    }>();

    const emit = defineEmits<{
        (e: "change", value: string): void
    }>();
</script>
