<template>
    <rapi-doc
        :spec-url="$store.getters['doc/resourceUrl']('kestra.yml')"
        theme="dark"
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
    import "rapidoc";
</script>

<style lang="scss" scoped>
    rapi-doc {
        background: transparent;
        width: 100%;
    }

</style>
