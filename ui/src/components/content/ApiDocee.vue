<template>
    <rapi-doc
        :specUrl="docStore.resourceUrl('kestra-ee.yml')"
        :theme="theme"
        renderStyle="view"
        showHeader="false"
        showInfo="false"
        allowAuthentication="false"
        allowServerSelection="false"
        allowTry="false"
        regularFont="Public Sans"
        monoFont="Source Code Pro"
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

<style scoped lang="scss">
    rapi-doc {
        background: transparent;
        width: 100%;
    }

</style>
