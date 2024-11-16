<template>
    <label>
        {{ label }}<template v-if="comparator"><span>:{{ comparator }}:</span></template>
        <template v-if="value">{{ value }}</template>
    </label>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps({
        option: {type: Object, required: true}
    });

    const DATE_FORMATS = {timeStyle: "short", dateStyle: "short"};
    const formatter = new Intl.DateTimeFormat("en-US", DATE_FORMATS);

    const getFilterValue = (value, label, comparator) => {
        if (!value.length) {
            return;
        }
        if (label !== "absolute_date" && comparator !== "between") {
            return `${value.join(", ")}`;
        }
        const {startDate, endDate} = value[0];
        return `${startDate ? formatter.format(new Date(startDate)) : "Unknown"}:and:${endDate ? formatter.format(new Date(endDate)) : "Unknown"}`;
    };

    const label = computed(() => props.option.label);
    const comparator = computed(() => props.option?.comparator?.value);
    const value = computed(() => getFilterValue(props.option?.value, label.value, comparator.value));
</script>

<style lang="scss" scoped>
    span {
        color: var(--bs-primary);
    }
</style>