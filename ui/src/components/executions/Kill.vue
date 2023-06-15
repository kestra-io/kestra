<template>
    <component
        :is="component"
        :icon="StopCircleOutline"
        @click="kill"
        v-if="enabled"
        class="me-1"
    >
        {{ $t('kill') }}
    </component>
</template>

<script setup>
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
</script>

<script>

    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";

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
        methods: {
            kill() {
                this.$toast()
                    .confirm(this.$t("killed confirm", {id: this.execution.id}), () => {
                        return this.$store
                            .dispatch("execution/kill", {
                                id: this.execution.id,
                            })
                            .then(() => {
                                this.$toast().success(this.$t("killed done"));
                            })
                    });
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.execution.namespace))) {
                    return false;
                }

                return State.isKillable(this.execution.state.current);
            }
        }
    };
</script>

<style lang="scss" scoped>
    button.el-button {
        cursor: pointer !important;
    }
</style>
