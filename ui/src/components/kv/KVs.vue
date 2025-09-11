<template>
    <TopNavBar :title="routeInfoTitle">
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
    <section data-component="FILENAME_PLACEHOLDER" class="d-flex flex-column fill-height container padding-bottom">
        <KVTable />
    </section>
</template>

<script setup>
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useNamespacesStore} from "override/stores/namespaces.js";
    import useRouteContext from "../../composables/useRouteContext";
    import Plus from "vue-material-design-icons/Plus.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import KVTable from "./KVTable.vue";

    const namespacesStore = useNamespacesStore();

    const {t} = useI18n({useScope: "global"});
    const routeInfoTitle = computed(() => t("kv.name"));
    useRouteContext(routeInfoTitle);
</script>