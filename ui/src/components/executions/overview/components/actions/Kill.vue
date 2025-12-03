<template>
    <el-dropdown v-if="enabled" placement="bottom-end" class="kill-dropdown">
        <el-button :icon="Circle" @click="kill(true)">
            {{ t("kill") }}
        </el-button>
        <template #dropdown>
            <el-dropdown-menu class="m-dropdown-menu">
                <el-dropdown-item
                    :icon="StopCircleOutline"
                    size="large"
                    @click="kill(true)"
                >
                    {{ t('kill parents and subflow') }}
                </el-dropdown-item>
                <el-dropdown-item
                    :icon="StopCircleOutline"
                    size="large"
                    @click="kill(false)"
                >
                    {{ t('kill only parents') }}
                </el-dropdown-item>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>
<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import Circle from "vue-material-design-icons/Circle.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";

    import {State} from "@kestra-io/ui-libs";

    import {useExecutionsStore} from "../../../../../stores/executions";
    import {useAuthStore} from "override/stores/auth";
    import {useToast} from "../../../../../utils/toast";
    import action from "../../../../../models/action";
    import permission from "../../../../../models/permission";

    const props = defineProps({
        execution: {
            type: Object,
            required: true
        }
    });

    const {t} = useI18n();
    const authStore = useAuthStore();
    const executionsStore = useExecutionsStore();
    const toast = useToast();

    const user = computed(() => authStore.user);

    const enabled = computed(() => {
        if (!(user.value && user.value.isAllowed(permission.EXECUTION, action.DELETE, props.execution.namespace))) {
            return false;
        }

        return State.isKillable(props.execution.state.current);
    });

    function kill(isOnKillCascade: boolean) {
        toast.confirm(t("killed confirm", {id: props.execution.id}), () => {
            return executionsStore
                .kill({
                    id: props.execution.id,
                    isOnKillCascade: isOnKillCascade
                })
                .then(() => {
                    toast.success(t("killed done"));
                });
        });
    }
</script>

<style scoped lang="scss">
    .kill-dropdown {
        width: 100%;
    }

    .m-dropdown-menu {
        width: fit-content !important;

        :deep(.el-dropdown-menu__item:hover) {
            background-color: var(--ks-log-background-error) !important;
            color: var(--ks-content-error) !important;
        }
    }
</style>
