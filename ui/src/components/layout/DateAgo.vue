<template>
    <span v-if="date" :class="className">
        {{ inverted ? full : from }}
    </span>
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";

    const props = defineProps({
        date: {
            type: [Date, String],
            default: undefined
        },
        inverted: {
            type: Boolean,
            default: false
        },
        format: {
            type: String,
            default: undefined
        },
        className: {
            type: String,
            default: null
        }
    })

    const {$moment, $filters} = getCurrentInstance()?.appContext.config.globalProperties || {} as any;

    const from = computed(() => {
        return $moment(props.date).fromNow();
    })
    const full = computed(() => {
        return $filters.date(props.date, props.format);
    })
</script>
