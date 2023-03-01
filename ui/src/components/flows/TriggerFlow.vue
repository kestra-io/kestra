<template>
    <div class="trigger-flow-wrapper">
        <el-button class="edit-flow-trigger-button" :icon="icon.Flash" :disabled="disabled" size="large" :type="type" @click="onClick">
            {{ $t('New execution') }}
        </el-button>
        <el-dialog v-if="isOpen" v-model="isOpen" destroy-on-close :append-to-body="true">
            <template #title>
                <span v-html="$t('execute the flow', {id: flowId})" />
            </template>
            <flow-run @execution-trigger="closeModal" :redirect="true" />
        </el-dialog>
    </div>
</template>


<script>
    import FlowRun from "./FlowRun.vue";
    import {mapState} from "vuex";
    import {executeTask} from "../../utils/submitTask"
    import Flash from "vue-material-design-icons/Flash.vue";
    import {shallowRef} from "vue";
    import {pageFromRoute} from "../../utils/eventsRouter";
    import action from "../../models/action";

    export default {
        components: {
            FlowRun,
        },
        props: {
            flowId: {
                type: String,
                required: true
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
                default: "info"
            },
        },
        data() {
            return {
                isOpen: false,
                icon: {
                    Flash: shallowRef(Flash)
                }
            };
        },
        mounted() {
            if (!this.flow && this.flowId && this.namespace) {
                this.$store
                    .dispatch("flow/loadFlow", {
                        id: this.flowId,
                        namespace: this.namespace,
                        deleted: true
                    });
            }
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

                if (!this.flow.inputs || this.flow.inputs.length === 0) {
                    this.$toast().confirm(
                        this.$t("execute flow now ?"),
                        () => executeTask(this, this.flow, {}, {
                            id: this.flowId,
                            namespace: this.namespace,
                            redirect: true
                        }),
                        () => this.isOpen = false
                    )
                } else {
                    this.isOpen = !this.isOpen
                }
            },

            closeModal() {
                this.isOpen = false;
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
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
            }
        }
    };
</script>

<style scoped>
.trigger-flow-wrapper {
    display: inline;
}
</style>