<template>
    <top-nav-bar :title="routeInfo.title" v-if="!isFullScreen() && !embed" />
    <Layout
        :title="t('demos.audit-logs.title')"
        :image="{source: sourceImg, alt: t('demos.audit-logs.title')}"
    >
        <template #message>
            {{ $t('demos.audit-logs.message') }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    // @ts-expect-error no types in TopNavBar yet
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/audit-logs.png";
    import useRouteContext from "../../mixins/useRouteContext";

    const {t} = useI18n();

    defineProps({
        embed: {
            type:Boolean,
            default: false
        }
    });

    const routeInfo = ref({
        title: t("demos.audit-logs.title"),
    });

    useRouteContext(routeInfo);

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen");
    }
</script>