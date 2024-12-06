<template>
    <span class="text-center d-block img-block">
        <img
            v-bind="$attrs"
            :alt="alt"
            :src="store.getters['doc/resourceUrl'](src)"
            loading="lazy"
        >
    </span>
</template>

<script setup lang="ts">
    import {useStore} from "vuex";

    const store = useStore();

    withDefaults(defineProps<{
        src?: string,
        alt?: string,
        width?: string | number,
        height?: string | number,
        class?: string
    }>(), {
        src: "",
        alt: "",
        width: "100%",
        height: "auto",
        class: ""
    });
</script>

<style lang="scss" scoped>
    img {
        max-width: 100%;
    }
</style>