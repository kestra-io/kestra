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

    const format = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) ?? "llll";
    const date = computed(() => moment(props.field)?.format(format) ?? props.field);
</script>
