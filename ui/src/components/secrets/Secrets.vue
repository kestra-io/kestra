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
            <div class="no-secret-manager-block d-flex flex-column gap-6 mt-5">
                <div class="header-block d-flex align-items-center">
                    <div class="d-flex flex-column">
                        <div class="d-flex flex-column gap-2">
                            <div class="d-flex flex-column align-items-start justify-content-center">
                                <img :src="sourceImg" :alt="$t('demos.secrets.title')" class="img-wrapper">
                                <div class="enterprise-tag">
                                    <div class="flare" />
                                    {{ $t('demos.enterprise_edition') }}
                                </div>
                                <h5 class="fw-bold mb-4 m-auto">
                                    {{ $t('demos.secrets.title') }}
                                </h5>
                            </div>
                        </div>
                        <div>
                            <div v-if="isOnline" class="video-container">
                                <iframe
                                    src="https://www.youtube.com/embed/u0yuOYG-qMI"
                                />
                            </div>
                            <p>{{ $t('demos.secrets.message') }}</p>
                            <DemoButtons class="mb-3" />
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
    @import "@kestra-io/ui-libs/src/scss/color-palette.scss";

    .no-secret-manager-block {
        padding: 0 7.75rem;

        *[style*="display: none"] { display: none !important }

        .header-block {
            border-bottom: 1px solid var(--ks-border-primary);

            p {
                font-size: .875rem;
            }

            .img-wrapper {
                width: 250px;
                height: 200px;
                overflow: visible;
                direction: rtl;
                margin: 0 auto;
            }
        }

        .enterprise-tag::before,
        .enterprise-tag::after{
            content: "";
            display: block;
            position: absolute;
            border-radius: 1rem;
        }

        .enterprise-tag::before{
            z-index: -2;
            background-image: linear-gradient(138.8deg, #CCE8FE 0%, #CDA0FF 27.03%, #8489F5 41.02%, #CDF1FF 68.68%, #B591E9 94%, #CCE8FE 100%);
            background-size: 200% 200%;
            top: 0px;
            bottom: 0px;
            left: 0px;
            right: 0px;
            animation: move-border 3s linear infinite;
        }

        .enterprise-tag::after{
            z-index: -1;
            background: $base-gray-100;
            top: 1px;
            left: 1px;
            bottom: 1px;
            right: 1px;
            html.dark & {
                background: $base-gray-400;
            }
        }

        .enterprise-tag{
            position: relative;
            top: -1.5rem;
            background: $base-gray-200;
            padding: .125rem 1rem;
            border-radius: 1rem;
            display: inline-block;
            z-index: 2;
            margin: 0 auto;
            html.dark &{
                background: #FBFBFB26;
            }
            .flare{
                display: none;
                position: absolute;
                content: "";
                height: 2rem;
                width: 2rem;
                z-index: 12;
                top: -1.1rem;
                right: 0;
                background-image:
                    // vertical flare
                    linear-gradient(0deg, rgba($base-gray-200, 0) 0%, $base-gray-200 50%, rgba($base-gray-200, 0) 100%),
                    // horizontal flare
                    linear-gradient(90deg, rgba($base-gray-200, 0) 0%, $base-gray-200 50%, rgba($base-gray-200, 0) 100%),
                    // flare effect
                    radial-gradient(circle, $base-gray-200 0%, rgba($base-gray-200, .1) 50%,rgba($base-gray-200, 0) 70%);
                background-size:  1px 100%, 100% 1px, 40% 40%;
                background-repeat: no-repeat;
                background-position: center, center, center;
                transform:rotate(-13deg);
                &::before{
                    content: "";
                    display: block;
                    position: absolute;
                    height: 2rem;
                    width: 2rem;
                    background-image:
                        // vertical flare
                        linear-gradient(0deg, rgba($base-gray-200, 0) 0%, rgba($base-gray-200, .7) 50%, rgba($base-gray-200, 0) 100%),
                        // horizontal flare
                        linear-gradient(90deg, rgba($base-gray-200, 0) 0%, rgba($base-gray-200, .7) 50%, rgba($base-gray-200, 0) 100%);
                    background-size:  1px 50%, 50% 1px;
                    background-repeat: no-repeat;
                    background-position: center, center, center;
                    transform: rotate(45deg);
                }
                html.dark &{
                    display: block;
                }
            }
        }

        @keyframes move-border {
            0%{background-position: 0% 0%}
            50%{background-position: 100% 100%}
            100%{background-position: 0% 0%}
        }

        .text-secondary {
            color: var(--ks-content-secondary) !important;

            .bold {
                font-weight: bold;
            }
        }

        .video-container {
            width: 640px;
            height: 360px;
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
    }
</style>