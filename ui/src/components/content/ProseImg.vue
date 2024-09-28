<template>
    <span class="text-center d-block img-block">
        <img
            v-bind="$attrs"
            :alt="alt"
            :src="$store.getters['doc/resourceUrl'](src)"
            loading="lazy"
        >
    </span>
</template>

<script setup lang="ts">
    defineProps({
        src: {
            type: String,
            default: ""
        },
        alt: {
            type: String,
            default: ""
        },
        width: {
            type: [String, Number],
            default: undefined
        },
        height: {
            type: [String, Number],
            default: undefined
        },
        class: {
            type: String,
            default: ""
        }
    })
</script>

<style lang="scss" scoped>
    img {
        max-width: 100%;
    }
</style>