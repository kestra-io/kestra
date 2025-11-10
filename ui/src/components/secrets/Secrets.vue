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
    <section
        class="d-flex flex-column fill-height padding-bottom"
        :class="miscStore.configs?.secretsEnabled === undefined ? 'mt-0 p-0' : 'container'"
    >
        <EmptyTemplate v-if="miscStore.configs?.secretsEnabled === undefined" class="d-flex flex-column text-start m-0 p-0 mw-100">
            <div class="no-secret-manager-block d-flex flex-column gap-6 mt-3">
                <div class="header-block d-flex align-items-center">
                    <div class="d-flex flex-column">
                        <div class="d-flex flex-row gap-2">
                            <div class="d-flex flex-column align-items-start justify-content-center">
                                <h5 class="fw-bold">
                                    {{ $t('demos.secrets.title') }}
                                </h5>
                                <p>{{ $t('demos.secrets.message') }}</p>
                            </div>
                            <img :src="sourceImg" :alt="$t('demos.secrets.title')" class="img-wrapper">
                        </div>
                        <div>
                            <div v-if="isOnline" class="video-container">
                                <iframe
                                    src="https://www.youtube.com/embed/u0yuOYG-qMI"
                                />
                            </div>
                            <DemoButtons />
                        </div>
                    </div>
                </div>
                <p class="mb-0">
                    {{ $t('demos.secrets.detected_env') }}
                </p>
                <div v-if="hasData === false">
                    <p class="text-tertiary mb-4">
                        {{ $t('demos.secrets.empty_env') }}
                    </p>
                    <div class="text-secondary">
                        <p class="bold mb-0">
                            {{ $t('demos.secrets.add_env.intro') }}
                        </p>
                        <ul>
                            <li v-html="$t('demos.secrets.add_env.first')" />
                            <li v-html="$t('demos.secrets.add_env.second')" />
                            <li v-html="$t('demos.secrets.add_env.third')" />
                        </ul>
                    </div>
                </div>
                <SecretsTable
                    v-show="hasData === true"
                    :filterable="false"
                    keyOnly
                    :namespace="miscStore.configs?.systemNamespace ?? 'system'"
                    :addSecretModalVisible="addSecretModalVisible"
                    @update:add-secret-modal-visible="addSecretModalVisible = $event"
                    @has-data="hasData = $event"
                />
            </div>
        </EmptyTemplate>
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
    import sourceImg from "../../assets/demo/secrets.png";
    import DemoButtons from "../demo/DemoButtons.vue";
    import EmptyTemplate from "../layout/EmptyTemplate.vue";

    const miscStore = useMiscStore();

    const props = defineProps({
        namespace: {
            type: String,
            default: undefined
        }
    });

    const addSecretModalVisible = ref(false);
    const hasData = ref(undefined);

    const {t} = useI18n({useScope: "global"});
    const routeInfo = computed(() => ({title: t("secret.names")}));

    useRouteContext(routeInfo);
</script>

<style scoped lang="scss">
    .no-secret-manager-block {
        padding: 0 10.75rem;

        *[style*="display: none"] { display: none !important }

        .header-block {
            border-bottom: 1px solid var(--ks-border-primary);

            p {
                font-size: .875rem;
            }

            .img-wrapper {
                width: 350px;
                height: 300px;
                overflow: visible;
                direction: rtl;
                flex-shrink: 0;
            }
        }

        .text-secondary {
            color: var(--ks-content-secondary) !important;

            .bold {
                font-weight: bold;
            }
        }

        .video-container {
            width: 100%;
            max-width: 640px;
            aspect-ratio: 16 / 9;
            margin-bottom: 1rem;
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
            padding: 0 4rem;

            .header-block {
                .img-wrapper {
                    width: 250px;
                    height: 214px;
                }
            }
        }

        @media (max-width: 992px) {
            padding: 0 2rem;

            .header-block {
                .d-flex.flex-row {
                    flex-direction: column !important;
                    align-items: center;
                    text-align: center;

                    .d-flex.flex-column {
                        align-items: center !important;
                    }
                }

                .img-wrapper {
                    width: 200px;
                    height: 171px;
                    direction: ltr;
                }
            }
        }

        @media (max-width: 768px) {
            padding: 0 1.5rem;

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
            padding: 0 1rem;

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
</style>
