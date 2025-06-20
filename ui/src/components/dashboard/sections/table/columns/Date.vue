<template>
    <span> {{ date }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import moment from "moment";

    import {DATE_FORMAT_STORAGE_KEY} from "../../../../../components//settings/BasicSettings.vue";

    const props = defineProps({
        field: {
            type: String,
            default: undefined,
        },
    });

    const format = localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "llll";
    const date = computed(() => moment(props.field)?.format(format) ?? props.field);
</script>
