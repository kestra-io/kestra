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

    const formatDateIfPresent = (rawDate: string|undefined) => {
        if(rawDate){
            // moment(date) always return a Moment, if the date is undefined, it will return current date, we don't want that here
            return dateFilter(rawDate);
        } else {
            return undefined;
        }
    }
    const date = computed(() => formatDateIfPresent(props.field));
</script>
