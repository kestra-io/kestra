<template>
    <top-nav-bar :title="routeInfo.title" />
    <Layout
        :title="t(`demos.apps.title`)"
        :image="{source: sourceImg, alt: t(`demos.apps.title`)}"
    >
        <template #message>
            {{ $t(`demos.apps.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    // @ts-expect-error no types in TopNavBar yet
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/apps.png";
    import useRouteContext from "../../mixins/useRouteContext";

    const {t} = useI18n();

    const routeInfo = ref({
        title: t("demos.apps.title"),
    });

    useRouteContext(routeInfo);
</script>