<template>
    <el-breadcrumb class="p-4">
        <el-breadcrumb-item
            v-for="(breadcrumb, index) in breadcrumbs"
            :key="index"
            class="item"
            @click="
                (store.commit('code/removeBreadcrumb', {position: index}),
                 store.commit('code/unsetPanel', false))
            "
        >
            <router-link :to="breadcrumb.to">
                {{ breadcrumb.label }}
            </router-link>
        </el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script setup lang="ts">
    import {computed, onMounted, watch} from "vue";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({flow: {type: Object, required: true}});

    store.commit("code/clearBreadcrumbs");

    const breadcrumbs = computed(() => store.state.code.breadcrumbs);

    const params = {
        namespace: route.params.namespace,
        id: props.flow.id ?? "new",
        tab: "edit",
    };

    onMounted(() => {
        store.commit("code/addBreadcrumbs", {
            breadcrumb: {
                label:
                    route.name === "flows/create"
                        ? t("create_flow")
                        : props.flow.id,
                to: {name: route.name, params, query: {}},
            },
            position: 0,
        });
    });

    watch(
        () => route.query.identifier,
        (value) => {
            if (!value) return;

            store.commit("code/addBreadcrumbs", {
                breadcrumb: {
                    label:
                        route.query.identifier === "new"
                            ? t(`no_code.creation.${route.query.section}`)
                            : route.query.identifier,
                    to: {name: route.name, params, query: route.query},
                },
                position: 1,
            });
        },
    );
</script>

<style scoped lang="scss">
@import "../styles/code.scss";

.item:last-child > .el-breadcrumb__inner > a {
    color: $code-primary !important;
}
</style>
