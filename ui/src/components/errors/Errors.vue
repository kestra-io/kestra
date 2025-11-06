<template>
    <TopNavBar :title="routeInfo.title" v-if="!isFullScreen()" />
    <EmptyTemplate>
        <img :src="sourceImg" :alt="$t('errors.' + code + '.title')" class="img">
        <h2>{{ $t("errors." + code + ".title") }}</h2>

        <p>
            <span v-html="$t('errors.' + code + '.content')" />
        </p>

        <el-button v-if="!isFullScreen()" tag="router-link" :to="{name: 'home'}" type="primary" size="large">
            {{ $t("back_to_dashboard") }}
        </el-button>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import EmptyTemplate from "../../components/layout/EmptyTemplate.vue";
    import {useCoreStore} from "../../stores/core";
    import sourceImg from "../../assets/errors/kestra-error.png";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();

    const props = defineProps<{
        code: number | string;
    }>();

    const coreStore = useCoreStore();
    const route = useRoute();

    const routeInfo = computed(() => ({title: t("errors." + props.code + ".title")}));

    useRouteContext(routeInfo);

    const isFullScreen = () => {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen");
    };

    watch(
        () => route,
        () => {
            coreStore.error = undefined;
        }
    );
</script>

<style scoped lang="scss">
    .img {
        margin-top: 7rem;
        max-height: 156px;
    }

    h2 {
        line-height: 30px;
        font-size: 20px;
        font-weight: 600;
    }

    p {
        line-height: 22px;
        font-size: 14px;
    }
</style>
