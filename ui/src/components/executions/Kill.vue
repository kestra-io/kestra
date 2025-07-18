<template>
    <el-dropdown v-if="enabled" placement="bottom-end">
        <el-button type="default" :icon="Circle" @click="kill(true)">
            {{ $t("kill") }}
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
    import Circle from "vue-material-design-icons/Circle.vue";
</script>
<script>
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
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
                        return this.executionsStore
                            .kill({
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
            ...mapStores(useExecutionsStore),
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
        border-color: var(--ks-border-error);
        color: var(--ks-content-error);
    }
    .m-dropdown-menu {
        width: fit-content !important;
        
        :deep(.el-dropdown-menu__item:hover) {
            background-color: var(--ks-log-background-error) !important;
            color: var(--ks-content-error) !important;
        }
    }
</style>
