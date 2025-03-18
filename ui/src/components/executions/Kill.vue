<template>
    <el-dropdown v-if="enabled">
        <el-button type="default" @click="kill(true)">
            {{ $t("kill") }}
            <DotsVertical title="" />
        </el-button>
        <template #dropdown>
            <el-dropdown-menu class="m-dropdown-menu">
                <el-dropdown-item
                    :icon="StopCircleOutline"
                    size="large"
                    @click="kill(true)"
                >
                    {{ $t('kill parents and subflow') }}
                </el-dropdown-item>
                <el-dropdown-item
                    :icon="StopCircleOutline"
                    size="large"
                    @click="kill(false)"
                >
                    {{ $t('kill only parents') }}
                </el-dropdown-item>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>
<script setup>
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
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
        },
        methods: {
            kill(isOnKillCascade) {
                this.$toast()
                    .confirm(this.$t("killed confirm", {id: this.execution.id}), () => {
                        return this.$store
                            .dispatch("execution/kill", {
                                id: this.execution.id,
                                isOnKillCascade: isOnKillCascade
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
