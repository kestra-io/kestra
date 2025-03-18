<template>
    <Layout
        :title="t(`demos.blueprints.title`)"
        :image="{source: sourceImg, alt: t(`demos.blueprints.title`)}"
        :embed="props.embed"
    >
        <template #message>
            {{ $t(`demos.blueprints.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import Layout from "./Layout.vue";

    import sourceImg from "../../assets/demo/blueprints.png";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n();

    const props = defineProps({embed: {type: Boolean, default: false}});
</script>
