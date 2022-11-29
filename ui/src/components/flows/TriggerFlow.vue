<template>
    <div class="trigger-flow-wrapper">
        <el-button :disabled="disabled" @click="onClick">
            <kicon>
                <flash /> {{ $t('New execution') }}
            </kicon>
        </el-button>
        <el-dialog v-if="isOpen" v-model="isOpen" :title="$t('execute the flow')" destroy-on-close :append-to-body="true">
            <flow-run @execution-trigger="closeModal" :redirect="true" />
        </el-dialog>
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
        data() {
            return {
                isOpen: false
            };
        },
        mounted() {
            if (!this.flow) {
                this.$store
                    .dispatch("flow/loadFlow", {
                        id: this.flowId,
                        namespace: this.namespace,
                    });
            }
        },
        methods: {
            onClick() {
                if (!this.flow.inputs || this.flow.inputs.length === 0) {
                    this.$toast().confirm(
                        this.$t("execute flow now ?"),
                        () => executeTask(this, this.flow, {
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
        }
    };
</script>
<style scoped>
.trigger-flow-wrapper {
    display: inline;
}
</style>