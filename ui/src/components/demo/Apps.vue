<template>
    <TopNavBar :title="routeInfo.title" />
    <Layout
        :title="t(`demos.apps.title`)"
        :image="{source: sourceImg, alt: t(`demos.apps.title`)}"
        :video="{
            source: 'https://www.youtube.com/embed/KwBO8mcS3kk',
        }"
    >
        <template #message>
            {{ $t(`demos.apps.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/apps.png";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    const routeInfo = computed(() => ({title: t("demos.apps.title")}));

    useRouteContext(routeInfo);
</script>