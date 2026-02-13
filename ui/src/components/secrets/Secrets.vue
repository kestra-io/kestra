<template>
    <Navbar :title="routeInfo.title">
        <template #additional-right v-if="miscStore.configs?.secretsEnabled">
            <ul>
                <li>
                    <el-button :icon="Plus" type="primary" @click="addSecretModalVisible = true">
                        {{ $t('secret.add') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </Navbar>
    <section class="d-flex flex-column fill-height padding-bottom container">
        <div v-if="miscStore.configs?.secretsEnabled === undefined" class="d-flex flex-column text-start m-0 p-0 mw-100">
            <div class="oss-secrets-block d-flex flex-column gap-4">
                <SecretsTable
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
            <div class="no-secret-manager-block d-flex flex-column gap-6">
                <div class="header-block d-flex align-items-center">
                    <div class="ee-promo-layout">
                        <div v-if="isOnline" class="video-container">
                            <iframe
                                src="https://www.youtube.com/embed/u0yuOYG-qMI"
                            />
                        </div>
                        <div class="ee-promo-content d-flex flex-column">
                            <div class="d-flex flex-row gap-2">
                                <div class="d-flex flex-column align-items-start justify-content-center">
                                    <div class="ee-tag-wrap">
                                        <EnterpriseTag>
                                            {{ $t('demos.enterprise_edition') }}
                                        </EnterpriseTag>
                                    </div>
                                    <h5 class="fw-bold">
                                        {{ $t('demos.secrets.title') }}
                                    </h5>
                                    <p>{{ $t('demos.secrets.message') }}</p>
                                </div>
                            </div>
                            <DemoButtons />
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <SecretsTable
            v-else
            filterable
            :addSecretModalVisible="addSecretModalVisible"
            :namespace="props.namespace"
            @update:add-secret-modal-visible="addSecretModalVisible = $event"
        />
    </section>
</template>

<script setup lang="ts">
    import {useNetwork} from "@vueuse/core"
    const {isOnline} = useNetwork()

    import SecretsTable from "./SecretsTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Navbar from "../layout/TopNavBar.vue";
    import {useI18n} from "vue-i18n";
    import {computed, ref} from "vue";
    import useRouteContext from "../../composables/useRouteContext";
    import {useMiscStore} from "override/stores/misc";
    import DemoButtons from "../demo/DemoButtons.vue";
    import EnterpriseTag from "../EnterpriseTag.vue";

    const miscStore = useMiscStore();

    const props = defineProps({
        namespace: {
            type: String,
            default: undefined
        }
    });

    const addSecretModalVisible = ref(false);
    const hasData = ref<boolean>();

    const {t} = useI18n({useScope: "global"});
    const routeInfo = computed(() => ({title: t("secret.names")}));

    useRouteContext(routeInfo);
</script>

<style scoped lang="scss">
    .no-secret-manager-block {
        padding: 0 0 1.5rem;

        *[style*="display: none"] { display: none !important }

        .header-block {
            p {
                font-size: .875rem;
            }

        }

        .ee-promo-layout {
            display: flex;
            gap: 1.5rem;
            align-items: center;
        }

        .ee-promo-content {
            flex: 1;
        }

        .video-container {
            width: 100%;
            flex: 1;
            aspect-ratio: 16 / 9;
            border-radius: 8px;
            border: 1px solid var(--ks-border-primary);
            overflow: hidden;

            iframe {
                width: 100%;
                height: 100%;
                border: 0;
            }
        }

        @media (max-width: 1200px) {
            .ee-promo-layout {
                flex-direction: column;
            }
        }

        @media (max-width: 992px) {
            .header-block {
                .d-flex.flex-row {
                    flex-direction: column !important;
                    align-items: center;
                    text-align: center;

                    .d-flex.flex-column {
                        align-items: center !important;
                    }
                }
            }
        }

        @media (max-width: 768px) {
            .header-block {

                p {
                    font-size: 0.8125rem;
                }
            }

            .video-container {
                max-width: 100%;
            }
        }

        @media (max-width: 576px) {
            .header-block {

                h5 {
                    font-size: 1.125rem;
                }

                p {
                    font-size: 0.75rem;
                }
            }
        }
    }

    .oss-secrets-block {
        padding: 0;
    }

    .oss-secrets-hint {
        text-align: left;

        ul,
        li {
            font-size: .875rem;
        }
    }

    .secrets-divider {
        border-top: 1px solid var(--ks-border-primary);
    }

    .ee-tag-wrap {
        :deep(.enterprise-tag) {
            margin: 0 0 0.5rem 0;
        }
    }
</style>
