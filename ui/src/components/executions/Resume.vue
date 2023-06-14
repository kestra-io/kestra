<template>
    <component
        :is="component"
        :icon="PlayBox"
        @click="resume"
        v-if="enabled"
        class="me-1"
    >
        {{ $t('resume') }}
    </component>
</template>
<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";

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
            resume() {
                this.$toast()
                    .confirm(this.$t("resumed confirm", {id: this.execution.id}), () => {
                        return this.$store
                            .dispatch("execution/resume", {
                                id: this.execution.id,
                            })
                            .then(() => {
                                this.$toast().success(this.$t("resumed done"));
                            })
                    });
            }
        },
        computed: {
            PlayBox() {
                return PlayBox
            },
            ...mapState("auth", ["user"]),
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                return State.isPaused(this.execution.state.current);
            }
        }
    };
</script>

<style lang="scss" scoped>
    button.el-button {
        cursor: pointer !important;
    }
</style>
