<template>
    <TopNavBar :title="routeInfo.title" />
    <Layout
        :title="t(`demos.blueprints.title`)"
        :image="{source: sourceImg, alt: t(`demos.blueprints.title`)}"
        :video="{
            source: 'https://www.youtube.com/embed/qbGfK-FJi6s?si=UTeK3V5Cj8FRHH91',
        }"
        :embed="props.embed"
    >
        <template #message>
            {{ $t(`demos.blueprints.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import Layout from "./Layout.vue";
    import {computed} from "vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    import sourceImg from "../../assets/demo/blueprints.png";

    import {useI18n} from "vue-i18n";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    const routeInfo = computed(() => ({title: t("demos.blueprints.title")}));

    useRouteContext(routeInfo);

    const props = defineProps({embed: {type: Boolean, default: false}});
</script>
