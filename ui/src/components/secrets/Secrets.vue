<template>
    <Navbar :title="routeInfo.title">
        <template #additional-right v-if="configs?.secretsEnabled">
            <ul>
                <li>
                    <el-button :icon="Plus" type="primary" @click="addSecretModalVisible = true">
                        {{ $t('secret.add') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </Navbar>
    <section data-component="FILENAME_PLACEHOLDER" class="d-flex flex-column fill-height container padding-bottom" :class="configs?.secretsEnabled === undefined ? 'min-w-auto ms-auto me-auto' : ''">
        <template v-if="configs?.secretsEnabled === undefined">
            <Layout
                :title="$t('demos.secrets.title')"
                :image="{source: sourceImg, alt: $t('demos.secrets.title')}"
            >
                <template #message>
                    {{ $t('demos.secrets.message') }}
                </template>
            </Layout>
            <el-divider />
            <p>Here are secret-type environment variables identified at instance start-time:</p>
        </template>
        <SecretsTable
            :filterable="configs?.secretsEnabled !== undefined"
            :key-only="configs?.secretsEnabled === undefined"
            :namespace="configs?.secretsEnabled === true ? undefined : (configs?.systemNamespace ?? 'system')"
            :add-secret-modal-visible="addSecretModalVisible"
            @update:add-secret-modal-visible="addSecretModalVisible = $event"
        />
    </section>
</template>

<script setup>
    import SecretsTable from "./SecretsTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Navbar from "../layout/TopNavBar.vue";
    import {useI18n} from "vue-i18n";
    import {computed, ref} from "vue";
    import {useStore} from "vuex";
    import useRouteContext from "../../mixins/useRouteContext.js";
    import sourceImg from "../../assets/demo/secrets.png";
    import Layout from "../demo/Layout.vue";

    const store = useStore();

    const configs = computed(() => store.getters["misc/configs"]);

    const addSecretModalVisible = ref(false);

    const {t} = useI18n({useScope: "global"});
    const routeInfo = computed(() => ({title: t("secret.names")}));

    useRouteContext(routeInfo);
</script>

<style lang="scss" scoped>
    :deep(.message-block) {
        width: 100%;
    }
</style>