<template>
    <Navbar :title="routeInfo.title">
        <template #actions v-if="miscStore.configs?.secretsEnabled">
            <ul>
                <li>
                    <KsButton :icon="Plus" type="primary" @click="addSecretModalVisible = true">
                        {{ $t('secret.add') }}
                    </KsButton>
                </li>
            </ul>
        </template>
    </Navbar>
    <section :class="miscStore.configs?.secretsEnabled === undefined ? 'd-flex flex-column fill-height container' : 'full-container'">
        <div v-if="miscStore.configs?.secretsEnabled === undefined" class="d-flex flex-column text-start m-0 p-0 mw-100">
            <div class="oss-secrets-block d-flex flex-column gap-4">
                <SecretsTable
                    v-if="hasData !== false"
                    v-show="hasData === true"
                    :filterable="false"
                    keyOnly
                    :namespace="miscStore.configs?.systemNamespace ?? 'system'"
                    :addSecretModalVisible="addSecretModalVisible"
                    @update:add-secret-modal-visible="addSecretModalVisible = $event"
                    @has-data="hasData = $event"
                />
                <div v-if="hasData === false" class="oss-secrets-hint">
                    <h6 class="fw-bold mb-1">
                        {{ $t('demos.secrets.add_env.intro') }}
                    </h6>
                    <ul class="mb-0">
                        <li v-html="$t('demos.secrets.add_env.first')" />
                        <li v-html="$t('demos.secrets.add_env.second')" />
                        <li v-html="$t('demos.secrets.add_env.third')" />
                    </ul>
                </div>
            </div>
            <div class="secrets-divider my-4" />
            <Empty type="secrets" demoCta :title="$t('demos.secrets.title')">
                <template #description>
                    {{ $t('demos.secrets.message') }}
                </template>
            </Empty>
        </div>
        <SecretsTable
            v-else
            filterable
            :addSecretModalVisible="addSecretModalVisible"
            :namespace="props.namespace"
            @update:add-secret-modal-visible="addSecretModalVisible = $event"
        >
            <template #empty>
                <Empty type="secrets">
                    <template v-if="miscStore.configs?.secretsEnabled" #button>
                        <KsButton :icon="Plus" type="primary" @click="addSecretModalVisible = true">
                            {{ $t('secret.add') }}
                        </KsButton>
                    </template>
                </Empty>
            </template>
        </SecretsTable>
    </section>
</template>

<script setup lang="ts">
    import SecretsTable from "./SecretsTable.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Navbar from "../layout/TopNavBar.vue"
    import Empty from "../layout/empty/Empty.vue"
    import {useI18n} from "vue-i18n"
    import {computed, ref} from "vue"
    import useRouteContext from "../../composables/useRouteContext"
    import useRestoreUrl from "../../composables/useRestoreUrl"
    import {useMiscStore} from "override/stores/misc"

    useRestoreUrl()

    const miscStore = useMiscStore()

    const props = defineProps({
        namespace: {
            type: String,
            default: undefined,
        },
    })

    const addSecretModalVisible = ref(false)
    const hasData = ref<boolean>()

    const {t} = useI18n({useScope: "global"})
    const routeInfo = computed(() => ({title: t("secret.names")}))

    useRouteContext(routeInfo)
</script>

<style scoped lang="scss">
    .oss-secrets-block {
        padding: 0;
    }

    .oss-secrets-hint {
        text-align: left;
        padding-inline-start: var(--ks-spacing-5);

        ul,
        li {
            font-size: var(--ks-font-size-sm);
        }
    }

    .secrets-divider {
        border-top: 1px solid var(--ks-border-default);
    }

    .ee-tag-wrap {
        :deep(.enterprise-tag) {
            margin: 0 0 0.5rem 0;
        }
    }
</style>
