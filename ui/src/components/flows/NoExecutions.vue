<template>
    <div class="main">
        <div class="section-1">
            <div class="section-1-main">
                <div class="section-content">
                    <img :src="logo" alt="Kestra" class="img-fluid" width="150px">
                    <h5 class="section-1-title mt-4">
                        {{ $t("no-executions-view.title") }} <span style="color: #bbbbff">Kestra</span>
                    </h5>
                    <p class="section-1-desc">
                        {{ $t("no-executions-view.sub_title") }}
                    </p>
                    <div v-if="flow && !flow.deleted" class="mt-2">
                        <trigger-flow
                            type="primary"
                            :disabled="flow.disabled"
                            :flow-id="flow.id"
                            :namespace="flow.namespace"
                            :flow-source="flow.source"
                        />
                    </div>
                </div>
                <div class="mid-bar mb-3">
                    <div class="title title--center-line" />
                </div>
                <div class="section-content">
                    <h6 class="section-1-title mt-2">
                        {{ $t("no-executions-view.guidance_desc") }}
                    </h6>
                    <p class="section-1-desc guidance">
                        {{ $t("no-executions-view.guidance_sub_desc") }}
                    </p>
                </div>
                <OverviewBottom />
            </div>
        </div>
    </div>
</template>

<script>
    import {mapGetters, mapState} from "vuex";
    import OverviewBottom from "../onboarding/execution/OverviewBottom.vue";
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import noexecutionimg from "../../assets/onboarding/noexecution.png";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import permission from "../../models/permission";
    import action from "../../models/action";

    export default {
        name: "ExecuteFlow",
        mixins: [RouteContext, RestoreUrl],
        components: {
            OverviewBottom,
            TriggerFlow,
        },
        props: {
            topbar: {
                type: Boolean,
                default: true,
            },
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"]),
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            logo() {
                return noexecutionimg;
            },
            canExecute() {
                return this.flow ? this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace) : false;
            },
        },
    };
</script>

<style scoped lang="scss">
.main {
	padding: 3rem 1rem 1rem;
	background: var(--el-text-color-primary);
	background: radial-gradient(ellipse at top, rgba(102, 51, 255, 0.6) 0%, rgba(253, 253, 253, 0) 20%);
	background-size: 4000px;
	background-position: center;
	height: 100vh;
	width: auto;
	display: flex;
	flex-direction: column;
	container-type: inline-size;
    
    @media (min-width: 768px) {
        padding: 3rem 2rem 1rem;
    }

    @media (min-width: 992px) {
        padding: 3rem 3rem 1rem;
    }

    @media (min-width: 1920px) {
        padding: 3rem 10rem 1rem;
    }
}

.img-fluid {
    max-width: 100%;
    height: auto;
}

:deep(.el-button) {
    font-weight: 500;
    font-size: var(--el-font-size-lg);
    padding: 1.25rem 3.2rem;
}

.main .section-1 {
    display: flex;
    flex-grow: 1;
    justify-content: center;
    align-items: center;

    .section-1-main {
        .section-content {
            width: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;

            .section-1-title {
                line-height: var(--el-font-line-height-primary);
                text-align: center;
                font-weight: 600;
                color: var(--el-text-color-regular);
            }

            .section-1-desc {
                margin-top: -10px;
                line-height: var(--el-font-line-height-primary);
                font-weight: 300;
                font-size: var(--el-font-size-extra-small);
                text-align: center;
                color: var(--el-text-color-regular);
            }

            .guidance {
                color: var(--el-text-color-secondary);
            }
        }

        .mid-bar {
            margin-top: 20px;

            .title {
                font-weight: 500;
                color: var(--bs-gray-900-lighten-5);
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: var(--el-font-size-extra-small );

                &--center-line {
                    padding: 0;

                    &::before {
                        content: "";
                        background-color: var(--bs-gray-600-lighten-10);
                        height: 1px;
                        width: 50%;
                    }
                }
            }
        }
    }
}
</style>
