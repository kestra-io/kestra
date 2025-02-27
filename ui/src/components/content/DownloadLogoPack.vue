<template>
    <a
        download
        :href="$store.getters['doc/resourceUrl']('kestra-logo-kit.zip')"
    >
        <LinkVariant />   {{ $t('download_logos') }}
    </a>
</template>

<script setup>
    import LinkVariant from "vue-material-design-icons/LinkVariant.vue";
</script>
