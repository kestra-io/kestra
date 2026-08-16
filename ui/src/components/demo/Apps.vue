<template>
    <TopNavBar :title="routeInfo.title" />
    <Layout
        :title="t(`${keyPrefix}.title`)"
        type="apps"
        :image="{source: sourceImg, alt: t(`${keyPrefix}.title`)}"
        :video="{
            source: 'https://www.youtube.com/embed/KwBO8mcS3kk',
        }"
    >
        <template #message>
            {{ $t(`${keyPrefix}.message`) }}
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

    const props = defineProps({
        blueprints: {
            type: Boolean,
            default: false,
        },
    });

    const {t} = useI18n();

    const keyPrefix = computed(() => props.blueprints ? "demos.apps.blueprints" : "demos.apps");

    const routeInfo = computed(() => ({title: props.blueprints ? t("blueprints.apps") : t("demos.apps.title")}));

    useRouteContext(routeInfo);
</script>
