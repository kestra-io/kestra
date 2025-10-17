<template>
    <TopNavBar :title="routeInfo.title" />
    <Layout
        :title="t(`demos.tests.title`)"
        :image="{source: sourceImg, alt: t(`demos.tests.title`)}"
        :video="{
            source: 'https://www.youtube.com/embed/jMZ9Cs3xxpo',
        }"
    >
        <template #message>
            {{ $t(`demos.tests.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/tests.png";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    const routeInfo = computed(() => ({title: t("demos.tests.header")}));

    useRouteContext(routeInfo);
</script>