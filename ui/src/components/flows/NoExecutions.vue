<template>
    <div class="main">
        <div class="section-1">
            <div class="section-1-main">
                <div class="section-content">
                    <img
                        :src="logo"
                        alt="Kestra"
                        class="section-1-img img-fluid"
                        width="200px"
                    >
                    <h2 class="section-1-title">
                        {{ $t("no-executions-view.title") }} <span class="highlight">Kestra</span>
                    </h2>
                    <p class="section-1-desc">
                        {{ $t("no-executions-view.sub_title") }}
                    </p>
                    <div v-if="flow && !flow.deleted" class="trigger-wrapper">
                        <TriggerFlow
                            type="primary"
                            :disabled="flow.disabled"
                            :flowId="flow.id"
                            :namespace="flow.namespace"
                            :flowSource="flow.source"
                        />
                    </div>
                </div>
                <el-divider>
                    {{ $t("welcome_page.guide") }}
                </el-divider>
                <OverviewBottom />
            </div>
        </div>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import TriggerFlow from "../flows/TriggerFlow.vue"
    import OverviewBottom from "../onboarding/execution/OverviewBottom.vue";
    import noexecutionimg from "../../assets/onboarding/noexecution.svg"
    import {useFlowStore} from "../../stores/flow"
    interface Props {
        topbar?: boolean
    }

    withDefaults(defineProps<Props>(), {
        topbar: true,
    })

    const flowStore = useFlowStore();
    const flow = computed(() => flowStore.flow)
    const logo = computed(() => noexecutionimg)
    // const logoDark = computed(() => noexecutionimgDark)
</script>

<style scoped lang="scss">
    .main {
        padding: 5rem 1rem 1rem;
        background: var(--ks-background-body);
        transform: translateY(-40px);
        background: radial-gradient(ellipse at top, rgba(102,51,255,0.6) 0%, rgba(253, 253, 253, 0) 20%);
        background-size: 4000px;
        background-position: center;
        height: 100%;
        width: auto;
        display: flex;
        flex-direction: column;
        container-type: inline-size;

        @media (min-width: 768px) {
            padding: 5rem 2rem 1rem;
        }

        @media (min-width: 992px) {
            padding: 5rem 3rem 1rem;
        }

        @media (min-width: 1920px) {
            padding: 5rem 10rem 1rem;
        }
    }

    .trigger-wrapper {
        margin-top: 0.5rem;
        margin-bottom: 2rem;
        display: flex;
        justify-content: center;
    }
     .highlight {
                    color: var(--ks-content-link);
                }

    .product-link, .watch {
        font-weight: 700;
        border-radius: 5px;
        text-decoration: none;
        font-size: var(--el-font-size-small);
        width: 200px;
        margin: 0;
        margin-bottom: 1rem;
    }

    .watch {
        font-weight: 500;
        background-color: var(--ks-button-background-secondary);
        font-size: var(--el-font-size-small);
    }

    .main .section-1 {
        display: flex;
        flex-grow: 1;
        justify-content: center;
        align-items: center;
        border-radius: var(--bs-border-radius);
    }
    .section-1-main {
        .section-content {
            width: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;
            margin-bottom: 2rem;
            .section-1-img {
                max-width: 120%;
                height: auto;
                object-fit: contain;
            }
            .section-1-title {
                line-height: var(--el-font-line-height-primary);
                text-align: center;
                font-size: var(--el-font-size-extra-large);
                font-weight: 600;
                color: var(--ks-content-primary);
            }

            .section-1-desc {
                line-height: var(--el-font-line-height-primary);
                font-weight: 300;
                font-size: 0.875rem;
                text-align: center;
                color: var(--ks-content-primary);
            }
        }
    }

    :deep(.el-divider__text) {
        color: var(--ks-content-secondary);
        white-space: nowrap;
        font-size: var(--el-font-size-extra-small);
    }

    @container (max-width: 20px) {
        .main .section-1 .section-1-main {
            width: 90%;
        }
    }

    @container (max-width: 50px) {
        .main .section-1 .section-1-main {
            padding-top: 30px;
        }

        .section-1 .section-1-main .container {
            width: 76%;
        }
    }

</style>
