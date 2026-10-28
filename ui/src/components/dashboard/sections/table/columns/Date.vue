<template>
    <span> {{ date }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {date as dateFilter} from "../../../../../utils/filters";

    const props = defineProps({
        field: {
            type: String,
            default: undefined,
        },
    });

    // moment(undefined) resolves to now, so an absent field must not reach the formatter.
    const date = computed(() => props.field ? dateFilter(props.field) : props.field);
</script>
