<template>
    <rapi-doc
        v-if="ready"
        :spec-url="docStore.resourceUrl('kestra.yml')"
        :theme="theme"
        render-style="view"
        show-header="false"
        show-info="false"
        allow-authentication="false"
        allow-server-selection="false"
        allow-try="false"
        regular-font="Public Sans"
        mono-font="Source Code Pro"
    />
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import {useDocStore} from "../../stores/doc";
    
    const docStore = useDocStore();
    const ready = ref(false)
    // @ts-expect-error rapidoc is not typed
    import("rapidoc").then(() => {
        ready.value = true
    });


    const theme = ref(localStorage.getItem("theme") === "dark" ? "dark" : "light")
</script>

<style lang="scss" scoped>
    rapi-doc {
        background: transparent;
        width: 100%;
    }
</style>
