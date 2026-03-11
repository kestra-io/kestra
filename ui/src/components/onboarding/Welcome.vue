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

    <section class="container">
        <el-row justify="center">
            <el-col :xs="24" :sm="24" :md="18" :lg="16" :xl="14">
                <AiCopilot :flow :conversationId />

                <el-tag
                    v-for="(label, index) in labels"
                    :key="index"
                    @click="flow = flows[label]"
                >
                    {{ label }}
                </el-tag>
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";

    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import AiCopilot from "../ai/AiCopilot.vue";

    import {flows, labels} from "./flows/index";

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

    import Utils from "../../utils/utils";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n();

    useRestoreUrl();

    const routeInfo = computed(() => ({title: t("ai.flow.title")}));
    useRouteContext(routeInfo);

    const flow = ref<string>(flows.initial);
    const conversationId = ref<string>(Utils.uid());
</script>

<style scoped lang="scss"></style>
