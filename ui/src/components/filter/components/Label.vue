<template>
    <span v-if="label">{{ $t(`filters.options.${label}`) }}</span>
    <span v-if="comparator" class="comparator">{{ comparator }}</span>
    <span v-if="value">{{ !comparator ? ":" : "" }}{{ value }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps({option: {type: Object, required: true}});

    import moment from "moment";
    const DATE_FORMAT = localStorage.getItem("dateFormat") || "llll";

    const formatter = (date) => moment(date).format(DATE_FORMAT);

    const label = computed(() => props.option?.label);
    const comparator = computed(() => props.option?.comparator?.label);
    const value = computed(() => {
        const {value, label, comparator} = props.option;

        if (!value.length) return;

        if (label !== "absolute_date" && comparator !== "between") {
            return `${value.join(", ")}`;
        }

        const {startDate, endDate} = value[0];
        return `${startDate ? formatter(new Date(startDate)) : "unknown"}:and:${endDate ? formatter(new Date(endDate)) : "unknown"}`;
    });
</script>

<style lang="scss" scoped>
.comparator {
    background: var(--bs-gray-500);
    padding: 0.3rem 0.35rem;
    margin: 0 0.5rem;
    display: inline-block;
}
</style>
