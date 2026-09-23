<template>
    <TopNavBar :title="routeInfo.title" v-if="!isFullScreen()" />
    <EmptyTemplate>
        <img :src="sourceImg" :alt="$t('errors.' + code + '.title')" class="img">
        <h2>{{ $t("errors." + code + ".title") }}</h2>

        <p>
            <span v-html="$t('errors.' + code + '.content')" />
        </p>

        <KsButton v-if="!isFullScreen()" tag="router-link" :to="{name: 'home'}" type="primary" size="large">
            {{ $t("back_to_dashboard") }}
        </KsButton>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import EmptyTemplate from "../../components/layout/EmptyTemplate.vue"
    import error404Img from "../../assets/errors/kestra-error.png"
    import error403Img from "../../assets/errors/kestra-error-403.png"
    import useRouteContext from "../../composables/useRouteContext"

    const {t} = useI18n()

    const props = defineProps<{
        code: number | string;
    }>()

    const sourceImg = computed(() =>
        String(props.code) === "403" ? error403Img : error404Img,
    )

    const routeInfo = computed(() =>
        ({title: t("errors." + props.code + ".title")}),
    )

    useRouteContext(routeInfo)

    const isFullScreen = () => {
        return document.getElementsByTagName("html")[0].classList.contains("full-screen")
    }
</script>

<style scoped lang="scss">
    .img {
        margin-top: 7rem;
        max-height: 156px;
    }

    h2 {
        line-height: 30px;
        font-size: var(--ks-font-size-lg);
        font-weight: 600;
    }

    p {
        line-height: 22px;
        font-size: var(--ks-font-size-sm);
    }
</style>
