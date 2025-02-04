<template>
    <el-tooltip
        effect="light"
        :persistent="false"
        transition=""
        :hide-after="0"
        :content="$t('Set labels tooltip')"
        raw-content
        :placement="tooltipPosition"
    >
        <component
            :is="component"
            :icon="LabelMultiple"
            @click="isOpen = !isOpen"
            :disabled="!enabled"
        >
            {{ $t("Set labels") }}
        </component>
    </el-tooltip>
    <el-dialog v-if="isOpen" v-model="isOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <h5>{{ $t("Set labels") }}</h5>
        </template>

        <template #footer>
            <el-button @click="isOpen = false">
                {{ $t("cancel") }}
            </el-button>
            <el-button type="primary" @click="setLabels()">
                {{ $t("ok") }}
            </el-button>
        </template>

        <p v-html="$t('Set labels to execution', {id: execution.id})" />

        <el-form>
            <el-form-item :label="$t('execution labels')">
                <label-input
                    v-model:labels="executionLabels"
                    :existing-labels="execution.labels"
                />
            </el-form-item>
        </el-form>
    </el-dialog>
</template>

<script setup>
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
</script>

<script>
    import {mapState} from "vuex";
    import LabelInput from "../../components/labels/LabelInput.vue";
    import {State} from "@kestra-io/ui-libs"

    import {filterLabels} from "./utils"
    import permission from "../../models/permission.js";
    import action from "../../models/action.js";

    export default {
        components: {LabelInput},
        props: {
            component: {
                type: String,
                default: "el-button"
            },
            execution: {
                type: Object,
                required: true
            },
            tooltipPosition: {
                type: String,
                default: "bottom"
            }
        },
        methods: {
            setLabels() {
                const filtered = filterLabels(this.executionLabels)
                if(filtered.error) {
                    this.$toast().error(this.$t("wrong labels"))
                    return;
                }

                this.isOpen = false;
                this.$store.dispatch("execution/setLabels", {
                    labels: filtered.labels,
                    executionId: this.execution.id
                }).then(response => {
                    this.$store.commit("execution/setExecution", response.data)
                    this.$toast().success(this.$t("Set labels done"));
                })
            },
        },
        computed: {
            ...mapState("auth", ["user"]),
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                return !State.isRunning(this.execution.state.current);
            }
        },
        data() {
            return {
                isOpen: false,
                executionLabels: []
            };
        },
        watch: {
            isOpen() {
                this.executionLabels = [];
                if (this.execution.labels) {
                    this.executionLabels = this.execution.labels
                }
            }
        },
    };
</script>
