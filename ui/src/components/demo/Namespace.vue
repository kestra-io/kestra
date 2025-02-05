<template>
    <Layout
        :title="t(`demos.namespace.${props.tab}.title`)"
        :image="{source: sourceImg, alt: t(`demos.namespace.${props.tab}.title`)}"
    >
        <template #message>
            {{ $t(`demos.namespace.${props.tab}.message`) }}
        </template>
    </Layout>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import Layout from "./Layout.vue";
    import {useStore} from "vuex";
    import sourceImg from "../../assets/demo/namespace.png";

    const {t} = useI18n();
    const store = useStore();

    store.commit("doc/setDocId", "namespace.management")

    const props = defineProps<{
        tab: string;
    }>();
</script>