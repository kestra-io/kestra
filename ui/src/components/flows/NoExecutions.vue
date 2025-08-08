<template>
    <div class="main">
        <div class="content">
            <div class="logo-section">
                <img :src="logo" alt="Kestra" class="logo" width="150px">
                <img :src="logoDark" alt="Kestra" class="logo-dark" width="150px">
                <h5 class="title">
                    {{ $t("no-executions-view.title") }} <span class="highlight">Kestra</span>
                </h5>
                <p class="description">
                    {{ $t("no-executions-view.sub_title") }}
                </p>
                <div v-if="flow && !flow.deleted" class="trigger-wrapper">
                    <TriggerFlow
                        type="primary"
                        :disabled="flow.disabled"
                        :flow-id="flow.id"
                        :namespace="flow.namespace"
                        :flow-source="flow.source"
                    />
                </div>
                <el-divider />
            </div>

            <div class="guidance-section">
                <h6 class="guidance-title">
                    {{ $t("no-executions-view.guidance_desc") }}
                </h6>
                <p class="description guidance">
                    {{ $t("no-executions-view.guidance_sub_desc") }}
                </p>
            </div>
            <OverviewBottom />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useStore} from "vuex"
    import OverviewBottom from "../onboarding/execution/OverviewBottom.vue"
    import TriggerFlow from "../flows/TriggerFlow.vue"
    import noexecutionimg from "../../assets/onboarding/noexecution.png"
    import noexecutionimgDark from "../../assets/onboarding/noexecutionDark.png"

    interface Props {
        topbar?: boolean
    }

    withDefaults(defineProps<Props>(), {
        topbar: true,
    })

    const store = useStore()

    const flow = computed(() => store.state.flow.flow)
    const logo = computed(() => noexecutionimg)
    const logoDark = computed(() => noexecutionimgDark)
</script>

<style scoped lang="scss">
.main {
    margin-top: -1.5rem;
    padding: 3rem 1rem 1rem;
    background: radial-gradient(ellipse at top, rgba(102, 51, 255, 0.1) 0, rgba(102, 51, 255, 0) 20%);
    background-color: var(--ks-background-body);
    background-size: 5000px 300px;
    background-position: top center;
    background-repeat: no-repeat;
    height: 100%;
    width: auto;
    container-type: inline-size;
    display: flex;
    flex-grow: 1;
    justify-content: center;
    align-items: center;

    @media (min-width: 768px) {
        padding: 3rem 2rem 1rem;
    }

    @media (min-width: 992px) {
        padding: 3rem 3rem 1rem;
    }

    @media (min-width: 1920px) {
        padding: 3rem 10rem 1rem;
    }

    .content {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        
        h5, h6, p {
            margin: 0;
        }

        .logo-section {
            display: flex;
            flex-direction: column;
            align-items: center;
            margin-top: 1rem;

            .logo {
                max-width: 100%;
                height: auto;
                
                html.dark & {
                    display: none;
                }
            }
            
            .logo-dark {
                display: none;
                
                html.dark & {
                    display: inline-block;
                }
            }

            .title {
                line-height: var(--el-font-line-height-primary);
                text-align: center;
                font-weight: 600;
                color: var(--ks-content-primary);
                margin-top: 2rem !important;

                .highlight {
                    color: var(--ks-content-link);
                }
            }

            .description {
                line-height: var(--el-font-line-height-primary);
                font-weight: 300;
                font-size: var(--el-font-size-extra-small);
                text-align: center;
                color: var(--ks-content-primary);
            }

            .trigger-wrapper {
                margin-top: 1.5rem;
            }
        }

        .guidance-section {
            display: flex;
            flex-direction: column;
            align-items: center;

            .guidance-title {
                line-height: var(--el-font-line-height-primary);
                text-align: center;
                font-weight: 600;
                color: var(--ks-content-primary);
                margin-top: 0.5rem;
            }

            .description {
                line-height: var(--el-font-line-height-primary);
                font-weight: 300;
                font-size: var(--el-font-size-extra-small);
                text-align: center;
                color: var(--ks-content-primary);

                &.guidance {
                    color: var(--ks-content-link);
                }
            }
        }
    }
}

:deep(.el-button) {
    font-weight: 500;
    font-size: var(--el-font-size-lg);
    padding: 1.25rem 3rem;
}

:deep(.el-divider--horizontal) {
    width: 90%;
    border-color: var(--ks-border-secondary);
}
</style>
