<template>
    <Navbar :title="routeInfo.title">
        <template #actions v-if="secretsEnabled">
            <ul>
                <li>
                    <KsButton :icon="Plus" type="primary" @click="addSecretModalVisible = true">
                        {{ $t('secret.add') }}
                    </KsButton>
                </li>
            </ul>
        </template>
    </Navbar>
    <section :class="secretsEnabled === undefined ? 'secrets-oss-layout fill-height container' : 'full-container'">
        <div v-if="secretsEnabled === undefined" class="secrets-oss-content">
            <div class="oss-secrets-block">
                <SecretsTable
                    v-if="hasData !== false"
                    v-show="hasData === true"
                    :filterable="false"
                    keyOnly
                    :namespace="systemNamespace"
                    :addSecretModalVisible="addSecretModalVisible"
                    @update:add-secret-modal-visible="addSecretModalVisible = $event"
                    @has-data="hasData = $event"
                />
                <div v-if="hasData === false" class="oss-secrets-hint">
                    <h6 class="oss-secrets-hint-title">
                        {{ $t('demos.secrets.add_env.intro') }}
                    </h6>
                    <ul class="oss-secrets-hint-list">
                        <li v-html="$t('demos.secrets.add_env.first')" />
                        <li v-html="$t('demos.secrets.add_env.second')" />
                        <li v-html="$t('demos.secrets.add_env.third')" />
                    </ul>
                </div>
            </div>
            <div class="secrets-divider" />
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
                    <template v-if="secretsEnabled" #button>
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
    import {useSystemNamespace} from "../../composables/useSystemNamespace"
    import {useMiscStore} from "override/stores/misc"
    import type {MiscControllerConfiguration} from "@kestra-io/kestra-sdk"

    useRestoreUrl()

    const miscStore = useMiscStore()
    const systemNamespace = useSystemNamespace()

    // secretsEnabled is an EE-only field: it isn't part of the OSS OpenAPI spec, so it's not on
    // the generated SDK type. undefined means OSS (unrestricted); a boolean means EE is reporting
    // whether the secrets backend is actually configured.
    type ConfigsWithSecrets = MiscControllerConfiguration & {secretsEnabled?: boolean}
    const secretsEnabled = computed(() => (miscStore.configs as ConfigsWithSecrets | undefined)?.secretsEnabled)

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

<style scoped>
    .secrets-oss-layout {
        display: flex;
        flex-direction: column;
    }

    .secrets-oss-content {
        display: flex;
        flex-direction: column;
        text-align: start;
        margin: 0;
        padding: 0;
        max-width: 100%;
    }

    .oss-secrets-block {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-5);
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

    .oss-secrets-hint-title {
        font-weight: 700;
        margin-bottom: var(--ks-spacing-1);
    }

    .oss-secrets-hint-list {
        margin-bottom: 0;
    }

    .secrets-divider {
        margin-block: var(--ks-spacing-5);
        border-top: 1px solid var(--ks-border-default);
    }
</style>
