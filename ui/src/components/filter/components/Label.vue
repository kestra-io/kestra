<template>
    <span v-if="label" class="text-lowercase">
        {{ $t(`filters.options.${label}`) }}
    </span>
    <span v-if="comparator" class="comparator">{{ comparator }}</span>
    <span v-if="value">{{ !comparator ? ":" : "" }}{{ value }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {CurrentItem} from "../utils/types";

    const props = defineProps<{ option: CurrentItem }>();

    import moment from "moment";
    const DATE_FORMAT = localStorage.getItem("dateFormat") || "llll";

    const formatter = (date: Date) => moment(date).format(DATE_FORMAT);

    const UNKNOWN = "unknown";

    const label = computed(() => props.option.label);
    const comparator = computed(() => props.option?.comparator?.label);
    const value = computed(() => {
        const {value, label, comparator} = props.option;

        if (!value.length) return;

        if (label !== "absolute_date" && comparator?.label !== "between") {
            return `${value.join(", ")}`;
        }

        if (typeof value[0] !== "string") {
            const {startDate, endDate} = value[0];
            return `${startDate ? formatter(new Date(startDate)) : UNKNOWN}:and:${endDate ? formatter(new Date(endDate)) : UNKNOWN}`;
        }

        return UNKNOWN;
    });
</script>

<style scoped lang="scss">

.comparator {
    display: inline-block;

    margin: 0 0.5rem;
    padding: 0.3rem 0.35rem;

    background: var(--ks-background-paused);
}
</style>
