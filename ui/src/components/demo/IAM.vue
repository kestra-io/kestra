<template>
    <TopNavBar :title="routeInfo.title" v-if="!isFullScreen()" />
    <Layout
        :title="$t('demos.IAM.title')"
        :image="{source: sourceImg, alt: t('demos.IAM.title')}"
        :video="{
            source: 'https://www.youtube.com/embed/9I87QZJPl1Y',
        }"
    >
        <template #message>
            {{ $t('demos.IAM.message') }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/IAM.png";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    const routeInfo = computed(() => ({title: t("demos.IAM.title")}));
    useRouteContext(routeInfo);

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen");
    }
</script>