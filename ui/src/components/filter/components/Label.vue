<template>
    <span v-if="label">{{ label }}</span>
    <span v-if="comparator" class="text-primary">:{{ comparator }}:</span>
    <span v-if="value">{{ value }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps({option: {type: Object, required: true}});

    const DATE_FORMATS: Intl.DateTimeFormatOptions = {timeStyle: "short", dateStyle: "short"};
    const formatter = new Intl.DateTimeFormat("en-US", DATE_FORMATS);

    const label = computed(() => props.option?.label);
    const comparator = computed(() => props.option?.comparator?.label);
    const value = computed(() => {
        const {value, label, comparator} = props.option;

        if (!value.length) return;

        if (label !== "absolute_date" && comparator !== "between") {
            return `${value.join(", ")}`;
        }
        const {startDate, endDate} = value[0];
        return `${startDate ? formatter.format(new Date(startDate)) : "unknown"}:and:${endDate ? formatter.format(new Date(endDate)) : "unknown"}`;
    });
</script>
