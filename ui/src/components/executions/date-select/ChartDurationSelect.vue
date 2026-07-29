<template>
    <DateSelect
        :value="props.modelValue"
        :options="presets"
        @change="onChange"
    />
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import DateSelect from "./DateSelect.vue"

    interface Preset {
        value: string;
        label: string;
    }

    const props = withDefaults(defineProps<{
        modelValue?: string;
    }>(), {
        modelValue: "PT336H", // default: 14 days
    })

    const emit = defineEmits<{
        (e: "update:modelValue", value: string): void;
    }>()

    const presets = computed<Preset[]>(() => [
        {value: "PT168H", label: "datepicker.last7days"},
        {value: "PT336H", label: "datepicker.last14days"},
        {value: "PT672H", label: "datepicker.last28days"},
    ])

    const onChange = (value: string | number | undefined) => {
        emit("update:modelValue", String(value ?? "PT336H"))
    }
</script>