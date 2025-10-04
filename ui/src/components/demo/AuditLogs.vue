<template>
    <TopNavBar :title="routeInfo.title" v-if="!isFullScreen() && !embed" />
    <Layout
        :title="t('demos.audit-logs.title')"
        :image="{source: sourceImg, alt: t('demos.audit-logs.title')}"
        :video="{
            source: 'https://www.youtube.com/embed/Qz24gBPGZHs',
        }"
    >
        <template #message>
            {{ t('demos.audit-logs.message') }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import sourceImg from "../../assets/demo/audit-logs.png";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    defineProps({
        embed: {
            type:Boolean,
            default: false
        }
    });

    defineOptions({
        name: "AuditLogsDemo",
        inheritAttrs: false,
    });

    const routeInfo = computed(() => ({title: t("demos.audit-logs.title")}));

    useRouteContext(routeInfo);

    function isFullScreen() {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen");
    }
</script>