<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <router-link v-if="canCreateFlow" :to="{name: 'flows/create'}">
                <el-button type="primary">
                    {{ $t("onboarding.welcome.self_serve_title") }}
                </el-button>
            </router-link>
        </template>
    </TopNavBar>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import TopNavBar from "../../components/layout/TopNavBar.vue";

    import permission from "../../models/permission";
    import action from "../../models/action";

    import {useAuthStore} from "override/stores/auth";
    const authStore = useAuthStore();

    const canCreateFlow = computed(() => {
        return authStore.user?.hasAnyActionOnAnyNamespace(
            permission.FLOW,
            action.CREATE,
        );
    });

    import useRestoreUrl from "../../composables/useRestoreUrl";
    import useRouteContext from "../../composables/useRouteContext";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n();

    useRestoreUrl();

    const routeInfo = computed(() => ({title: t("ai.flow.title")}));
    useRouteContext(routeInfo);
</script>

<style scoped lang="scss"></style>
