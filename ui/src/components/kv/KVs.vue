<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <el-button :icon="Plus" type="primary" @click="namespacesStore.addKvModalVisible = true">
                        {{ $t("kv.add") }}
                    </el-button>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <section class="d-flex flex-column fill-height container padding-bottom">
        <KVTable />
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useNamespacesStore} from "override/stores/namespaces";
    import useRouteContext from "../../composables/useRouteContext";
    import Plus from "vue-material-design-icons/Plus.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import KVTable from "./KVTable.vue";

    const namespacesStore = useNamespacesStore();

    const {t} = useI18n({useScope: "global"});
    const routeInfo = computed(() => ({title: t("kv.name")}));
    useRouteContext(routeInfo);
</script>