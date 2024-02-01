<template>
    <div class="trigger-flow-wrapper">
        <el-button :icon="icon.Flash" :type="type" :disabled="isDisabled()" @click="onClick()">
            {{ $t("execute") }}
        </el-button>
        <el-dialog v-if="isOpen" v-model="isOpen" destroy-on-close :append-to-body="true">
            <template #header>
                <span v-html="$t('execute the flow', {id: flowId})" />
            </template>
            <flow-run @execution-trigger="closeModal" :redirect="true" />
        </el-dialog>
    </div>
</template>


<script>
    import FlowRun from "./FlowRun.vue";
    import {mapState} from "vuex";
    import Flash from "vue-material-design-icons/Flash.vue";
    import {shallowRef} from "vue";
    import {pageFromRoute} from "../../utils/eventsRouter";

    export default {
        components: {
            FlowRun
        },
        props: {
            flowId: {
                type: String,
                default: undefined
            },
            namespace: {
                type: String,
                required: true
            },
            disabled: {
                type: Boolean,
                default: false
            },
            type: {
                type: String,
                default: "primary"
            }
        },
        data() {
            return {
                isOpen: false,
                icon: {
                    Flash: shallowRef(Flash)
                }
            };
        },
        methods: {
            onClick() {
                if (this.$tours["guidedTour"].isRunning.value && !this.guidedProperties.executeFlow) {
                    this.$store.dispatch("api/events", {
                        type: "ONBOARDING",
                        onboarding: {
                            step: this.$tours["guidedTour"].currentStep._value,
                            action: "next",
                        },
                        page: pageFromRoute(this.$router.currentRoute.value)
                    });
                    this.$tours["guidedTour"].nextStep();
                    return;
                }
                this.isOpen = !this.isOpen;
            },
            closeModal() {
                this.isOpen = false;
            },
            isDisabled() {
                return this.disabled || this.flow?.deleted;
            },
            loadDefinition() {
                this.$store.dispatch("flow/loadFlow", {
                    id: this.flowId,
                    namespace: this.namespace,
                    allowDeleted: true
                });
            }
        },
        computed: {
            ...mapState("flow", ["flow", "executeFlow"]),
            ...mapState("core", ["guidedProperties"]),
        },
        watch: {
            guidedProperties: {
                handler() {
                    if (this.guidedProperties.executeFlow) {
                        this.onClick();
                    }
                },
                deep: true
            },
            executeFlow: {
                handler() {
                    if (this.executeFlow && !this.isDisabled()) {
                        this.$store.commit("flow/executeFlow", false);
                        this.onClick();
                    }
                }
            },
            flowId: {
                handler() {
                    if (!this.flowId) {
                        return;
                    }

                    this.loadDefinition();
                },
                immediate: true
            }
        }
    };
</script>

<style scoped>
.trigger-flow-wrapper {
    display: inline;
}
</style>