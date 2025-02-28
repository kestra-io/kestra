<template>
    <span v-if="label" class="text-lowercase">
        {{ $t(`filters.options.${label}`) }}
    </span>
    <span v-if="operation" class="operation">
        {{
            Object.values(COMPARATORS).find((c) => c.value === operation)
                ?.label ?? "Unknown"
        }}
    </span>
    <span v-if="value">{{ !operation ? ":" : "" }}{{ value }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {CurrentItem} from "../utils/types";

    const props = defineProps<{ option: CurrentItem; prefix: string }>();

    import {useFilters} from "../composables/useFilters";
    const {COMPARATORS} = useFilters(props.prefix);

    import moment from "moment";
    const DATE_FORMAT = localStorage.getItem("dateFormat") || "llll";

    const formatter = (date: Date) => moment(date).format(DATE_FORMAT);

    const UNKNOWN = "unknown";

    const label = computed(() => props.option.label);
    const operation = computed(() => props.option?.operation ?? props.option?.comparator?.value ?? (props.option?.label === "text" ? "$eq" : undefined));
    const value = computed(() => {
        const {value, label, operation} = props.option;

        if (!value.length) return;
        if (label === "labels") {
            return Array.isArray(value) && value.length === 1 ? value[0] : value;
        }
        if (label !== "absolute_date" && operation !== "between") {
            return `${value.join(", ")}`;
        }

        if (typeof value[0] !== "string") {
            const {startDate, endDate} = value[0];
            if (startDate && endDate) {
                return `${startDate ? formatter(new Date(startDate)) : UNKNOWN}:and:${endDate ? formatter(new Date(endDate)) : UNKNOWN}`;
            }
        }

        return UNKNOWN;
    });
</script>

<style scoped lang="scss">
span {
    padding: 0.33rem 0.35rem;
    display: inline-block;

    &:first-child,
    .operation {
        background: var(--ks-tag-background);
    }
    .operation {
        border-left: 4px solid #ffffff;
        border-right: 4px solid #ffffff;

        html.dark & {
            border-color: #20232d;
        }
    }
}
</style>
