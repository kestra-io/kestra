<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title" />
    <Layout
        :title="t(`demos.assets.title`)"
        :image="{
            source: img,
            alt: t(`demos.assets.title`)
        }"
        :video="{
            source: 'https://www.youtube.com/embed/XhICXP_GXic',
        }"
    >
        <template #message>
            {{ $t(`demos.assets.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import img from "../../assets/demo/assets.png";
    import useRouteContext from "../../composables/useRouteContext";

    defineProps({
        topbar: {
            type: Boolean,
            default: true
        }
    });

    import Layout from "./Layout.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    const {t} = useI18n();

    const routeInfo = computed(() => ({title: t("demos.assets.header")}));

    useRouteContext(routeInfo);
</script>