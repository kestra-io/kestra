<template>
    <div class="trigger-flow-wrapper">
        <b-button :disabled="disabled" v-hotkey="keymap" @click="onSubmit" v-b-tooltip.hover.top="'(Ctrl + Enter)'">
            <kicon>
                <flash /> {{ $t('New execution') }}
            </kicon>
        </b-button>
        <b-modal size="lg" hide-footer id="trigger-flow" :title="$t('execute the flow')">
            <flow-run @onExecutionTrigger="closeModal" :redirect="true" />
        </b-modal>
    </div>
</template>
<script>
    import Flash from "vue-material-design-icons/Flash";
    import FlowRun from "./FlowRun";
    import {mapState} from "vuex";
    import {executeTask} from "../../utils/submitTask"
    import Kicon from "../Kicon"

    export default {
        components: {
            Flash,
            FlowRun,
            Kicon
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
        },
        created() {
        },
        methods: {
            triggerFlow() {
                if (!this.flow.inputs || this.flow.inputs.length === 0) {
                    this.$bvModal
                        .msgBoxConfirm(this.$t("execute flow now ?"), {})
                        .then(value => {
                            if (value) {
                                executeTask(this, this.flow, {
                                    id: this.flowId,
                                    namespace: this.namespace,
                                    redirect: true
                                })
                            }
                        });
                } else {
                    this.$bvModal.show("trigger-flow");
                }
            },
            onSubmit() {
                if (!this.flow) {
                    this.$store
                        .dispatch("flow/loadFlow", {
                            id: this.flowId,
                            namespace: this.namespace,
                        })
                        .then(this.triggerFlow);
                } else {
                    this.triggerFlow();
                }
            },
            closeModal() {
                this.$bvModal.hide("trigger-flow")
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            keymap () {
                return {
                    "ctrl+enter": this.onSubmit,
                }
            },
        }
    };
</script>
<style scoped>
.trigger-flow-wrapper {
    display: inline;
}
</style>