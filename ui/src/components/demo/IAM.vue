<template>
    <top-nav-bar :title="routeInfo.title" v-if="!isFullScreen()" />
    <Layout
        :title="$t('demos.IAM.title')"
        :image="{source: sourceImg, alt: t('demos.IAM.title')}"
    >
        <template #message>
            {{ $t('demos.IAM.message') }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    // @ts-expect-error no types in TopNavBar yet
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/IAM.png";
    import useRouteContext from "../../mixins/useRouteContext";

    const {t} = useI18n();

    const routeInfo = ref({
        title: t("demos.IAM.title"),
    });

    useRouteContext(routeInfo);

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen");
    }
</script>