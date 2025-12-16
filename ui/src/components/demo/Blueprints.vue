<template>
    <TopNavBar :title="routeInfo.title" />
    <Layout
        :title="t(`demos.blueprints-${props.type}.title`)"
        :image="{
            source: sourceImg,
            alt: t(`demos.blueprints-${props.type}.title`),
        }"
        v-bind="
            props.type === 'flow'
                ? {
                    video: {
                        source: 'https://www.youtube.com/embed/qbGfK-FJi6s?si=UTeK3V5Cj8FRHH91',
                    },
                }
                : {}
        "
        :embed="props.embed"
    >
        <template #message>
            {{ $t(`demos.blueprints-${props.type}.message`) }}
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

    const props = defineProps({
        embed: {type: Boolean, default: false},
        type: {type: String, required: true},
    });

    const routeInfo = computed(() => ({
        title: t(`demos.blueprints-${props.type}.title`),
    }));

    useRouteContext(routeInfo);
</script>
