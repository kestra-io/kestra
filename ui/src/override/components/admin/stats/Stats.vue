<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="container" v-show="ready">
        <Usages class="mb-2" @loaded="ready = true" />
        <EditionComparator class="mt-4" />
    </section>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import Usages from "../../../../components/admin/stats/Usages.vue";
    import EditionComparator from "../../../../components/admin/stats/EditionComparator.vue";
    import useRouteContext from "../../../../composables/useRouteContext";
    import {useI18n} from "vue-i18n";

    const ready = ref(false);

    const {t} = useI18n();

    const routeInfo = computed(() => ({
        title: t("system overview"),
    }));
    useRouteContext(routeInfo);
</script>