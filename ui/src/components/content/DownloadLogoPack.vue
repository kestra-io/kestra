<template>
    <a
        download
        :href="docStore.resourceUrl('kestra-logo-kit.zip')"
    >
        <LinkVariant />   {{ $t('download_logos') }}
    </a>
</template>

<script lang="ts" setup>
    import {useDocStore} from "../../stores/doc";
    import LinkVariant from "vue-material-design-icons/LinkVariant.vue";

    const docStore = useDocStore();
</script>