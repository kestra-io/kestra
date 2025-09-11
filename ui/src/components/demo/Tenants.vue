<template>
    <TopNavBar :title="routeInfoTitle" v-if="!isFullScreen()" />
    <Layout
        :title="t('demos.tenants.title')"
        :image="{source: sourceImg, alt: t('demos.tenants.title')}"
        :video="{
            source: 'https://www.youtube.com/embed/z4uzAyjKeoc',
        }"
    >
        <template #message>
            {{ $t('demos.tenants.message') }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/tenants.png";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    const routeInfoTitle = computed(() => t("demos.tenants.title"));

    useRouteContext(routeInfoTitle);

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen");
    }
</script>