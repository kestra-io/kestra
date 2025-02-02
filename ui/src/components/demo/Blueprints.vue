<template>
    <Layout
        :title="t(`demos.blueprints.title`)"
        :image="{source: sourceImg, alt: t(`demos.blueprints.title`)}"
    >
        <template #message>
            {{ $t(`demos.blueprints.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import sourceImg from "../../assets/demo/blueprints.png";

    const {t} = useI18n();
</script>