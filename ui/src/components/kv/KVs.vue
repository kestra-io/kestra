<template>
    <Navbar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <el-button :icon="Plus" type="primary" @click="addKvModalVisible = true">
                        {{ $t("kv.add") }}
                    </el-button>
                </li>
            </ul>
        </template>
    </Navbar>
    <section data-component="FILENAME_PLACEHOLDER" class="d-flex flex-column fill-height container padding-bottom">
        <KVTable
            :add-kv-modal-visible="addKvModalVisible"
            @update:add-kv-modal-visible="addKvModalVisible = $event"
        />
    </section>
</template>

<script setup>
    import KVTable from "./KVTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Navbar from "../layout/TopNavBar.vue";
    import {useI18n} from "vue-i18n";
    import {computed, ref} from "vue";

    const addKvModalVisible = ref(false);

    const {t} = useI18n({useScope: "global"});
    const routeInfo = computed(() => ({title: t("kv.name")}));
</script>