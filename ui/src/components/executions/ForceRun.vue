<template>
    <el-tooltip
        effect="light"
        :persistent="false"
        transition=""
        :hide-after="0"
        :content="$t('force run tooltip')"
        raw-content
    >
        <component
            :is="component"
            :icon="RunFast"
            @click="click"
            :disabled="!enabled"
            class="ms-0 me-1"
        >
            {{ $t('force run') }}
        </component>
    </el-tooltip>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <span v-html="$t('force run title', {id: execution.id})" />
        </template>
        <template #footer>
            <el-button :icon="QueueFirstInLastOut" type="primary" @click="forceRun()" native-type="submit">
                {{ $t('force run') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import RunFast from "vue-material-design-icons/RunFast.vue";
</script>

<script>
    import {mapState} from "vuex";
    import {State} from "@kestra-io/ui-libs";
    import permission from "../../models/permission";
    import action from "../../models/action";

    export default {
        props: {
            execution: {
                type: Object,
                required: true
            },
            component: {
                type: String,
                default: "el-button"
            },
        },
        data() {
            return {
                isDrawerOpen: false,
            };
        },
        methods: {
            click() {
                this.$toast()
                    .confirm(this.$t("force run confirm", {id: this.execution.id}), () => {
                        return this.forceRun();
                    });
            },
            forceRun() {
                this.$store
                    .dispatch("execution/forceRun", {
                        id: this.execution.id
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("force run done"));
                    });
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("execution", ["flow"]),
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                return State.isRunning(this.execution.state.current) || State.isQueued(this.execution.state.current);
            }
        },
    };
</script>

<style lang="scss" scoped>
    button.el-button {
        cursor: pointer !important;
    }
</style>
