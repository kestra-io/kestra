<template>
    <component
        :is="component"
        :icon="PauseBox"
        @click="click"
        :disabled="!enabled"
        class="ms-0 me-1"
    >
        {{ $t('pause') }}
    </component>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <span v-html="$t('pause title', {id: execution.id})" />
        </template>
        <template #footer>
            <el-button :icon="PauseBox" type="primary" @click="pause()" native-type="submit">
                {{ $t('pause') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"

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
                    .confirm(this.$t("pause confirm", {id: this.execution.id}), () => {
                        return this.pause();
                    });
            },
            pause() {
                this.$store
                    .dispatch("execution/pause", {
                        id: this.execution.id
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("pause done"));
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

                return State.isRunning(this.execution.state.current) && ! State.isPaused(this.execution.state.current);
            }
        },
    };
</script>

<style lang="scss" scoped>
    button.el-button {
        cursor: pointer !important;
    }
</style>
