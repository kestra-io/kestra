<template>
    <v-tour :name="TOUR_NAME" :options="TOUR_OPTIONS" :steps="steps">
        <template #default="tour">
            <transition name="fade">
                <v-step
                    v-if="currentStep(tour)"
                    :key="tour.currentStep"
                    :step="currentStep(tour)"
                    :is-first="tour.isFirst"
                    :is-last="tour.isLast"
                    :labels="tour.labels"
                    :highlight="tour.highlight"
                    :class="{
                        last: tour.isLast,
                        fullscreen: currentStep(tour).fullscreen,
                        color: tour.currentStep === 1,
                        condensed: currentStep(tour).condensed,
                    }"
                >
                    <template #header>
                        <img
                            v-if="tour.isFirst"
                            :src="Animation"
                            alt="Kestra"
                            class="animation"
                        >
                        <div
                            v-if="currentStep(tour).title"
                            class="title"
                            :class="{dark: currentStep(tour).keepDark, empty: !flows.length}"
                        >
                            <div v-if="currentStep(tour).icon">
                                <img
                                    :src="currentStep(tour).icon"
                                    :class="{jump: currentStep(tour).jump}"
                                >
                            </div>
                            <span v-html="tour.currentStep === 1 && !flows.length ? t('onboarding.no_flows') : currentStep(tour).title" />
                        </div>
                    </template>
                    <template #content>
                        <div
                            v-if="tour.isFirst"
                            v-html="currentStep(tour).content"
                            class="v-step__content"
                            :class="{dark: currentStep(tour).keepDark}"
                        />
                        <div v-if="tour.currentStep === 1" class="flows dark">
                            <el-button
                                v-for="(flow, flowIndex) in flows"
                                :key="`flow__${flowIndex}`"
                                tag="div"
                                :class="{active: activeFlow === flowIndex}"
                                class="card"
                                @click="activeFlow = flowIndex"
                            >
                                <p class="title mb-2">
                                    {{ flow.description }}
                                </p>
                                <div>
                                    <div
                                        v-for="(task, taskIndex) in allTasks(flow.tasks)"
                                        :key="`flow__${flowIndex}__icon__${taskIndex}`"
                                        class="image me-1"
                                    >
                                        <TaskIcon
                                            :cls="task"
                                            :icons="icons"
                                            :variable="ICON_COLOR"
                                            only-icon
                                        />
                                    </div>
                                </div>
                            </el-button>
                        </div>
                    </template>
                    <template #actions>
                        <Wrapper
                            v-if="
                                currentStep(tour).primary ||
                                    currentStep(tour).secondary
                            "
                            center
                        >
                            <Secondary
                                v-if="currentStep(tour).secondary"
                                :label="currentStep(tour).secondary"
                                @click="exploreOther(tour.currentStep)"
                            />
                            <Primary
                                v-if="currentStep(tour).primary"
                                :label="currentStep(tour).primary"
                                :high="[0, 1].includes(tour.currentStep)"
                                :full="
                                    ![0, 1].includes(tour.currentStep) ||
                                        !tour.isLast
                                "
                                :disabled="tour.currentStep === 1 && !flows.length"
                                @click="
                                    tour.isLast
                                        ? finishTour(tour.currentStep)
                                        : nextStep(tour)
                                "
                            />
                        </Wrapper>

                        <Teleport to="body">
                            <Wrapper v-if="!tour.isLast" left>
                                <Skip @click="skipTour(tour.currentStep)" />
                            </Wrapper>
                            <Wrapper right>
                                <Previous
                                    v-if="!tour.isFirst && !tour.isLast"
                                    @click="previousStep(tour.currentStep)"
                                />
                                <Finish
                                    v-if="tour.isLast"
                                    @click="finishTour(tour.currentStep, false)"
                                />
                            </Wrapper>
                        </Teleport>
                    </template>
                </v-step>
            </transition>
        </template>
    </v-tour>
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance, onMounted, ref, watch} from "vue";

    import {useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import Wrapper from "./components/buttons/Wrapper.vue";

    import Secondary from "./components/buttons/Secondary.vue";
    import Primary from "./components/buttons/Primary.vue";

    import Skip from "./components/buttons/Skip.vue";

    import Previous from "./components/buttons/Previous.vue";

    import Finish from "./components/buttons/Finish.vue";

    import {pageFromRoute} from "../../utils/eventsRouter";

    import {TaskIcon} from "@kestra-io/ui-libs";
    import Animation from "../../assets/onboarding/animation.gif";

    import LightningBolt from "../../assets/onboarding/icons/lightning-bolt.svg";
    import ArrowLeft from "../../assets/onboarding/icons/arrow-left.svg";
    import ArrowTop from "../../assets/onboarding/icons/arrow-top.svg";
    import ArrowRight from "../../assets/onboarding/icons/arrow-right.svg";

    import {editorViewTypes} from "../../utils/constants";

    const router = useRouter();
    const store = useStore();

    const icons = computed(() => store.state.plugin.icons);

    const {t} = useI18n({useScope: "global"});

    const updateStatus = () => localStorage.setItem("tourDoneOrSkip", "true");
    const dispatchEvent = (step, action) =>
        store.dispatch("api/events", {
            type: "ONBOARDING",
            onboarding: {
                step,
                action,
                template: store.getters["core/guidedProperties"].template,
            },
            page: pageFromRoute(router.currentRoute.value),
        });

    const TOUR_NAME = "guidedTour";
    const TOUR_OPTIONS = {highlight: true, useKeyboardNavigation: false};
    const TOURS = getCurrentInstance()?.appContext.config.globalProperties.$tours;

    const ICON_COLOR = computed(() => {
        return document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0 ? "--bs-heading-color" : undefined;
    });

    const STEP_OPTIONS = {
        modifiers: [
            {
                name: "offset",
                options: {
                    offset: ({placement}: { placement: string }) => {
                        switch (placement) {
                        case "right":
                            return [0, -175];
                        case "left":
                            return [0, -154];
                        case "bottom":
                            return [-30, 30];
                        default:
                            return [0, 0];
                        }
                    },
                },
            },
        ],
    };

    const activeFlow = ref(0);
    const flows = computed(() => store.state.core.tutorialFlows);

    const allTasks = (tasks) => {
        const uniqueTypes = new Set();
        const dockerBuild = "io.kestra.plugin.docker.Build";
        const dockerRun = "io.kestra.plugin.docker.Run";

        const collectTypes = (task) => {
            if (task && typeof task === "object") {
                const {type} = task;
                if (type) {
                    if (
                        (type === dockerBuild && uniqueTypes.has(dockerRun)) ||
                        (type === dockerRun && uniqueTypes.has(dockerBuild)) ||
                        type === "io.kestra.plugin.scripts.runner.docker.Docker"
                    ) {
                        return;
                    }
                    uniqueTypes.add(type);
                }
                Object.values(task).forEach(collectTypes);
            }
        };

        tasks.forEach(collectTypes);

        return Array.from(uniqueTypes).filter(type => type);
    };
    const offset = computed(() => {
        switch (flows.value[activeFlow.value].id) {
        case "business_processes":
        case "data_engineering_pipeline":
            return 94;
        case "business_automation":
            return 134;
        case "dwh_and_analytics":
        case "file_processing":
        case "infrastructure_automation":
        case "microservices_and_apis":
            return 174;
        default:
            return 134;
        }
    });

    watch(activeFlow, async (newValue) => {
        store.commit("core/setGuidedProperties", {
            template: flows.value[newValue].id,
        });
    });

    const properties = (step, c = true, p = true, s = false) => ({
        title: t(`onboarding.steps.${step}.title`),
        ...(c ? {content: t(`onboarding.steps.${step}.content`)} : {}),
        ...(p ? {primary: t(`onboarding.steps.${step}.primary`)} : {}),
        ...(s ? {secondary: t(`onboarding.steps.${step}.secondary`)} : {}),
    });
    const wait = (time) =>
        new Promise((resolve) => setTimeout(() => resolve(true), time));

    const toggleScroll = (enabled = true) => {
        const wrapper = document.getElementById("app");
        if(enabled){
            wrapper?.classList.remove("no-scroll")
        }else{
            wrapper?.classList.add("no-scroll");
        }
    };

    const steps = [
        {
            ...properties(0),
            fullscreen: true,
            keepDark: true,
            before: () => {
                toggleScroll(false);

                store.commit("core/setGuidedProperties", {
                    tourStarted: true,
                    fullscreen: true,
                });

                return wait(1);
            },
        },
        {
            ...properties(1, false),
            fullscreen: true,
            keepDark: true,
            nextStep: () => {
                router.push({
                    name: "flows/update",
                    params: {
                        namespace: "tutorial",
                        id: flows.value[activeFlow.value]?.id,
                        tab: "edit",
                    },
                });
                store.commit("core/setGuidedProperties", {
                    manuallyContinue: true,
                });
            },
            before: () => {
                store.commit("editor/updateOnboarding");

                store.commit("core/setGuidedProperties", {
                    tourStarted: true,
                    template: flows.value[activeFlow.value]?.id,
                });

                return wait(1);
            },
        },
        {
            ...properties(2),
            icon: ArrowLeft,
            target: "#editorWrapper",
            highlightElement: "#editorWrapper",
            params: {...STEP_OPTIONS, placement: "right"},
            before: () => {
                toggleScroll();
                return wait(1);
            },
        },
        {
            ...properties(3),
            icon: ArrowRight,
            target: ".combined-right-view.topology-display",
            highlightElement: ".combined-right-view.topology-display",
            params: {...STEP_OPTIONS, placement: "left"},
            before: () => {
                store.commit("editor/changeView", editorViewTypes.SOURCE_TOPOLOGY);
            }
        },
        {
            ...properties(4, true, false),
            icon: ArrowTop,
            condensed: true,
            hideNext: true,
            jump: true,
            target: "#execute-button",
            highlightElement: ".top-bar",
            params: {...STEP_OPTIONS, placement: "bottom"},
            before: () => {
                return wait(200);
            },
        },
        {
            ...properties(5, true, false),
            icon: ArrowTop,
            condensed: true,
            hideNext: true,
            jump: true,
            target: ".flow-run-trigger-button",
            highlightElement: "#execute-flow-dialog",
            params: {
                modifiers: [{name: "offset", options: {offset: () => [0, 70]}}],
                placement: "bottom",
            },
            before: () => {
                return wait(200)
            },
        },
        {
            ...properties(6, true, true, true),
            icon: LightningBolt,
            target: "#gantt",
            highlightElement: "#gantt",
            params: {
                modifiers: [
                    {
                        name: "offset",
                        options: {offset: () => [0, offset.value]},
                    },
                ],
                placement: "bottom",
            },
            before: () => wait(1),
        },
    ];

    const currentStep = (tour) => tour.steps[tour.currentStep];
    const nextStep = (tour) => {
        dispatchEvent(tour.currentStep, "next");

        const nextStep = currentStep(tour).nextStep;
        if(nextStep) {
            nextStep()
        } else {
            TOURS[TOUR_NAME].nextStep()
        }
    };
    const previousStep = (current) => {
        dispatchEvent(current, "previous");
        TOURS[TOUR_NAME].previousStep();
    };
    const skipTour = (current) => {
        toggleScroll();

        updateStatus();
        dispatchEvent(current, "skip");

        store.commit("core/setGuidedProperties", {tourStarted: false});

        TOURS[TOUR_NAME].stop();
    };
    const finishTour = (current, push = true) => {
        toggleScroll();

        updateStatus();
        dispatchEvent(current, "finish");
        dispatchEvent(current, "executed");

        store.commit("core/setGuidedProperties", {tourStarted: false});

        TOURS[TOUR_NAME].finish();

        if (push) router.push({name: "flows/create"});
    };
    const exploreOther = (current) => {
        finishTour(current);
        dispatchEvent(current, "explore");
        router.push({name: "flows/list", query: {namespace: "tutorial"}});
    };

    onMounted(() => {
        store.dispatch("core/readTutorialFlows");
    });
</script>

<style lang="scss">
$background: var(--card-bg);
$color: var(--bs-heading-color);

$background-primary: #1c1e27;
$background-secondary: #2f3342;
$border-color: #404559;
$border-color-active: #8405ff;
$white: #ffffff;

$step-max-width: 380px;
$last-step-max-width: 460px;
$animation-width: 415px;
$flow-card-width: 360px;
$flow-image-size-container: 36px;

.no-scroll {
    overflow: hidden;
}

.fullscreen {
    z-index: 9998 !important;
    max-width: 100% !important;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
}

#app .v-step {
    max-width: $step-max-width;
    padding: 2rem;

    &.last {
        max-width: $last-step-max-width;
    }

    &.condensed {
        & div.title {
            margin-bottom: 0.5rem;
        }

        & .v-step__content {
            margin-bottom: 0;
        }
    }

    &.fullscreen {
        background: $background-primary
            url("../../assets/onboarding/background.webp") no-repeat center;
        background-blend-mode: normal;
        background-size: contain;
        border-radius: 0px;
    }

    &:not(.fullscreen) {
        background: $background;
        border: 1px solid $border-color-active;
        border-radius: 8px;
    }

    &.color {
        background-blend-mode: color;
    }

    & img.animation {
        pointer-events: none;
        width: $animation-width;
    }

    & div.title {
        &.dark {
            color: $white;
        }

        @keyframes jump {
            0% {
                transform: translateY(0);
            }
            50% {
                transform: translateY(-10px);
            }
            100% {
                transform: translateY(0);
            }
        }

        & img.jump {
            animation: jump 2s infinite;
        }

        margin-bottom: 2rem;
        text-align: center;
        line-height: 3rem;
        font-size: 2rem;
        font-weight: 500;
        color: $color;

        &.empty {
            font-size: 1.2rem;
            margin-bottom: 0;
        }

        & div {
            height: 2rem;
            margin-bottom: 1rem;
        }
    }

    & .v-step__content {
        &.dark {
            color: $white;
        }

        border: none;
        margin-bottom: 2rem;
        text-align: center;
        line-height: 2rem;
        font-size: 1.2rem;
        color: $color;
    }

    & div.flows {
        display: grid;
        grid-template-columns: 1fr 1fr;
        padding: 2rem;
        gap: 1rem;

        & .el-button.card {
            height: unset;
        }

        & .el-button > span {
            display: unset;
        }

        & .card {
            margin: 0;
            padding: 1rem;
            width: $flow-card-width;
            background-color: $background;
            border: 1px solid $border-color;

            &.active,
            &:hover {
                border: 1px solid $border-color-active;
                background-color: rgba(202, 197, 218, 0.9);

                html.dark & {
                    background-color: rgba(202, 197, 218, 0.3);
                }
            }

            & .title {
                line-height: 2rem;
                font-size: 1.2rem;
                font-weight: 500;
                color: $color;
            }

            & .image {
                background: $background;
                display: inline-flex;
                justify-content: center;
                align-items: center;
                width: $flow-image-size-container;
                height: $flow-image-size-container;
                border: 1px solid #e6e5f6;
                border-radius: 8px;
                padding: 4px;

                html.dark & {
                    border: 1px solid $border-color;
                }
            }
        }
    }
}

body.v-tour--active .left.buttons *,
body.v-tour--active .right.buttons * {
    pointer-events: auto;
}

.v-tour__target--highlighted {
    z-index: 1040 !important; // To go over side menu
    box-shadow: 0 0 0 99999px rgba(0, 0, 0, 0.75) !important;
    border: 1px solid $border-color-active;
}

.v-tour__target--highlighted::before {
    content: "";
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    box-shadow: inset 0 0 10px 1px $border-color-active;
    border-radius: inherit;
    pointer-events: none;
    z-index: 10;
}

.v-step__arrow:before {
    display: none;
}
</style>
