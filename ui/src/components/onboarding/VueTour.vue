<template>
    <v-tour name="guidedTour" :steps="steps" :options="tourOptions">
        <template #default="tour">
            <transition name="fade">
                <v-step
                    v-if="tour.steps[tour.currentStep]"
                    :key="tour.currentStep"
                    :step="tour.steps[tour.currentStep]"
                    :is-first="tour.isFirst"
                    :is-last="tour.isLast"
                    :labels="tour.labels"
                    :highlight="true"
                    :class="tour.isFirst ? 'large-v-step': ''"
                >
                    <template #header>
                        <span v-if="tour.isFirst">
                            <div class="onboarding-card-img-yaml" />
                        </span>
                        <div class="v-step-indicator" v-if="!tour.isFirst">
                            {{ $t("Step") }} {{ tour.currentStep }}/ {{ tour.steps.length - 1 }}
                        </div>
                        <div class="v-step-title" v-if="tour.steps[tour.currentStep].header.title">
                            {{ tour.steps[tour.currentStep].header.title }}
                        </div>
                    </template>
                    <template #actions v-if="tour.isFirst">
                        <el-button class="v-step-button" @click="skipTour(tour.currentStep)" type="default" :icon="Close">
                            {{ $t("Skip tour") }}
                        </el-button>
                        <el-button
                            class="v-step-button"
                            @click="nextStep(tour.currentStep)"
                            type="primary"
                            :icon="ArrowRightCircleOutline"
                        >
                            {{ $t("Next step") }}
                        </el-button>
                    </template>
                    <template #actions v-else-if="tour.isLast">
                        <el-button class="v-step-button" @click="finishTour(tour.currentStep)" type="primary">
                            {{ $t("Finish") }}
                        </el-button>
                    </template>
                    <template #actions v-else>
                        <el-col>
                            <el-row justify="center">
                                <el-button
                                    size="small"
                                    class="v-step-button"
                                    @click="previousStep(tour.currentStep)"
                                    type="default"
                                    :icon="ArrowLeftCircleOutline"
                                >
                                    {{ $t("Previous step") }}
                                </el-button>
                                <el-button
                                    size="small"
                                    class="v-step-button"
                                    @click="nextStep(tour.currentStep)"
                                    type="primary"
                                    :icon="ArrowRightCircleOutline"
                                >
                                    {{ $t("Next step") }}
                                </el-button>
                            </el-row>
                            <el-row justify="center">
                                <el-button
                                    size="small"
                                    class="v-step-button"
                                    @click="skipTour(tour.currentStep)"
                                    type="default"
                                    :icon="Close"
                                >
                                    {{ $t("Skip tour") }}
                                </el-button>
                            </el-row>
                        </el-col>
                    </template>
                </v-step>
            </transition>
        </template>
    </v-tour>
</template>
<script setup>
    import Close from "vue-material-design-icons/Close.vue";
    import ArrowRightCircleOutline from "vue-material-design-icons/ArrowRightCircleOutline.vue";
    import ArrowLeftCircleOutline from "vue-material-design-icons/ArrowLeftCircleOutline.vue";
</script>
<script>
    import {mapGetters} from "vuex";
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
    import {pageFromRoute} from "../../utils/eventsRouter";
    export default {
        name: "VueTour",
        data() {
            return {
                tourOptions: {
                    useKeyboardNavigation: false,
                },
                steps: [
                    {
                        highlightElement: ".v-step",
                        header: {
                            title: this.$t("onboarding-content.step1.title"),
                        },
                        content: this.$t("onboarding-content.step1.content"),
                    },
                    {
                        target: "nav",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step2.title"),
                        },
                        content: this.$t("onboarding-content.step2.content"),
                        params: {
                            placement: "bottom"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: "",
                                monacoRange: undefined,
                                tourStarted: true
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: "#guided-right",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step3.title"),
                        },
                        content: this.$t("onboarding-content.step3.content"),
                        params: {
                            placement: "left"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts[0],
                                monacoRange: new monaco.Range(1, 1, 5, 1)
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: "#guided-right",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step4.title"),
                        },
                        content: this.$t("onboarding-content.step4.content"),
                        params: {
                            placement: "left"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts.slice(0, 2).join("\n"),
                                monacoRange: new monaco.Range(6, 1, 12, 1),
                                monacoDisableRange: new monaco.Range(1, 1, 5, 1)
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: "#guided-right",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step5.title"),
                        },
                        content: this.$t("onboarding-content.step5.content"),
                        params: {
                            placement: "left"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts.slice(0, 3).join("\n"),
                                monacoRange: new monaco.Range(13, 1, 16, 1),
                                monacoDisableRange: new monaco.Range(1, 1, 12, 1)
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: "#guided-right",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step6.title"),
                        },
                        content: this.$t("onboarding-content.step6.content"),
                        params: {
                            placement: "left"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts.slice(0, 4).join("\n"),
                                monacoRange: new monaco.Range(17, 1, 21, 1),
                                monacoDisableRange: new monaco.Range(1, 1, 16, 1)
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: "#guided-right",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step7.title"),
                        },
                        content: this.$t("onboarding-content.step7.content"),
                        params: {
                            placement: "left"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts.slice(0, 5).join("\n"),
                                monacoRange: new monaco.Range(22, 1, 27, 1),
                                monacoDisableRange: new monaco.Range(1, 1, 21, 1)
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: "#guided-right",
                        highlightElement: ".edit-flow-editor",
                        header: {
                            title: this.$t("onboarding-content.step8.title"),
                        },
                        content: this.$t("onboarding-content.step8.content"),
                        params: {
                            placement: "left"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts.slice(0, 6).join("\n"),
                                monacoRange: new monaco.Range(28, 1, 47, 1),
                                monacoDisableRange: new monaco.Range(1, 1, 27, 1)
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: ".edit-flow-save-button",
                        header: {
                            title: this.$t("onboarding-content.step9.title"),
                        },
                        content: this.$t("onboarding-content.step9.content"),
                        params: {
                            placement: "top"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {
                                ...this.guidedProperties,
                                source: this.flowParts.slice(0, 6).join("\n") + "\n",
                                monacoRange: null,
                                monacoDisableRange: null
                            });
                            resolve(true);
                        }),
                    },
                    {
                        target: ".edit-flow-trigger-button",
                        header: {
                            title: this.$t("onboarding-content.step10.title"),
                        },
                        content: this.$t("onboarding-content.step10.content"),
                        params: {
                            placement: "top"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {...this.guidedProperties, saveFlow: true});
                            this.dispatchEvent(this.$tours["guidedTour"].currentStep._value, "created")
                            setTimeout(() => {
                                resolve(true);
                            }, 300);
                        }),
                    },
                    {
                        target: ".flow-run-trigger-button",
                        header: {
                            title: this.$t("onboarding-content.step11.title"),
                        },
                        content: this.$t("onboarding-content.step11.content"),
                        params: {
                            placement: "bottom"
                        },
                        before: () => new Promise((resolve) => {
                            this.$store.commit("core/setGuidedProperties", {...this.guidedProperties, executeFlow: true});
                            localStorage.setItem("tourDoneOrSkip", "true");
                            setTimeout(() => {
                                resolve(true);
                            }, 300);
                        }),
                    }
                ],
                flowParts: [
                    "# " + this.$t("onboarding-flow.onboardComment1") + "\n" +
                        "# " + this.$t("onboarding-flow.onboardComment2") + "\n" +
                        "id: kestra-tour\n" +
                        "namespace: io.kestra.tour\n" +
                        "description: Kestra guided tour",
                    "# " + this.$t("onboarding-flow.inputs") + "\n" +
                        "inputs:\n" +
                        "  # " + this.$t("onboarding-flow.inputsDetails1") + "\n" +
                        "  # " + this.$t("onboarding-flow.inputsDetails2") + "\n" +
                        "  - name: csvUrl\n" +
                        "    type: STRING\n" +
                        "    defaults: https://www.data.gouv.fr/fr/datasets/r/d33eabc9-e2fd-4787-83e5-a5fcfb5af66d",
                    "# " + this.$t("onboarding-flow.tasks1") + "\n" +
                        "# " + this.$t("onboarding-flow.tasks2") + "\n" +
                        "# " + this.$t("onboarding-flow.tasks3") + "\n" +
                        "tasks:",
                    "  # " + this.$t("onboarding-flow.taskLog1") + "\n" +
                        "  # " + this.$t("onboarding-flow.taskLog2") + "\n" +
                        "  - id: log\n" +
                        "    type: io.kestra.core.tasks.debugs.Echo\n" +
                        "    format: The flow starts",
                    "  # " + this.$t("onboarding-flow.taskDL") + "\n" +
                        "  - id: downloadData\n" +
                        "    type: io.kestra.plugin.fs.http.Download\n" +
                        "    # " + this.$t("onboarding-flow.taskDLUri1") + "\n" +
                        "    # " + this.$t("onboarding-flow.taskDLUri2") + "\n" +
                        "    uri: \"{{inputs.csvUrl}}\"",
                    "  # " + this.$t("onboarding-flow.taskPython") + "\n" +
                        "  - id: analyseData\n" +
                        "    type: io.kestra.core.tasks.scripts.Python\n" +
                        "    inputFiles:\n" +
                        "      # " + this.$t("onboarding-flow.taskPythonCSV1") + "\n" +
                        "      # " + this.$t("onboarding-flow.taskPythonCSV2") + "\n" +
                        "      data.csv: \"{{outputs.downloadData.uri}}\"\n" +
                        "      # " + this.$t("onboarding-flow.taskPythonMain1") + "\n" +
                        "      # " + this.$t("onboarding-flow.taskPythonMain2") + "\n" +
                        "      # " + this.$t("onboarding-flow.taskPythonMain3") + "\n" +
                        "      main.py: |\n" +
                        "        import pandas as pd\n" +
                        "        from kestra import Kestra\n" +
                        "        data = pd.read_csv(\"data.csv\", sep=\";\")\n" +
                        "        data.info()\n" +
                        "        sumOfConsumption = data['conso'].sum()\n" +
                        "        Kestra.outputs({'sumOfConsumption': int(sumOfConsumption)})\n" +
                        "    # " + this.$t("onboarding-flow.taskPythonRequirements") + "\n" +
                        "    requirements:\n" +
                        "      - pandas"
                ]
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"]),
        },
        methods: {
            dispatchEvent(step, action) {
                this.$store.dispatch("api/events", {
                    type: "ONBOARDING",
                    onboarding: {
                        step: step,
                        action: action,
                    },
                    page: pageFromRoute(this.$router.currentRoute.value)
                });
            },
            nextStep(currentStep){
                this.dispatchEvent(currentStep,"next")
                return this.$tours["guidedTour"].nextStep();

            },
            previousStep(currentStep){
                this.dispatchEvent(currentStep,"previous")
                return this.$tours["guidedTour"].previousStep();
            },
            skipTour(currentStep) {
                this.dispatchEvent(currentStep,"skip")
                localStorage.setItem("tourDoneOrSkip", "true");
                this.$store.commit("core/setGuidedProperties", {
                    ...this.guidedProperties,
                    tourStarted:false,
                    saveFlow: false,
                    executeFlow: false,
                    validateInputs: true,
                    monacoRange: undefined,
                    monacoDisableRange: undefined});
                return this.$tours["guidedTour"].stop();
            },
            finishTour(currentStep) {
                this.dispatchEvent(currentStep,"finish")
                this.dispatchEvent(currentStep,"executed")
                localStorage.setItem("tourDoneOrSkip", "true");
                this.$store.commit("core/setGuidedProperties", {
                    ...this.guidedProperties,
                    tourStarted:false,
                    saveFlow: false,
                    executeFlow: false,
                    validateInputs: true,
                    monacoRange: undefined,
                    monacoDisableRange: undefined});
                return this.$tours["guidedTour"].finish();
            },
        }
    }
</script>
<style lang="scss">
.onboarding-card-img-yaml {
        margin: auto;
    // image height
    height: 130px;
        background: url("../../assets/onboarding/onboarding-yaml-light.svg") no-repeat center;
    html.dark & {
        background: url("../../assets/onboarding/onboarding-yaml-dark.svg") no-repeat center;
    }
}
</style>
