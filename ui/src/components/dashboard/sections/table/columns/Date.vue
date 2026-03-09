<template>
    <span> {{ date }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import moment from "moment";
    import {storageKeys} from "../../../../../utils/constants"

    const props = defineProps({
        field: {
            type: String,
            default: undefined,
        },
    });

    const momentLongDateFormat = "llll";
    const format = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) ?? momentLongDateFormat;
    const formatDateIfPresent = (rawDate: string|undefined) => {
        if(rawDate){
            // moment(date) always return a Moment, if the date is undefined, it will return current date, we don't want that here
            return moment(rawDate).format(format) ?? props.field;
        } else {
            return undefined;
        }
    }
    const date = computed(() => formatDateIfPresent(props.field));
</script>
