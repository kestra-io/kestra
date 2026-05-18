<template>
    <KsDropdown v-if="enabled" placement="bottom-end" class="kill-dropdown">
        <KsButton :icon="Circle" @click="kill(true)">
            {{ $t("kill") }}
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu class="m-dropdown-menu">
                <KsDropdownItem
                    :icon="StopCircleOutline"
                    size="large"
                    @click="kill(true)"
                >
                    {{ $t('kill parents and subflow') }}
                </KsDropdownItem>
                <KsDropdownItem
                    :icon="StopCircleOutline"
                    size="large"
                    @click="kill(false)"
                >
                    {{ $t('kill only parents') }}
                </KsDropdownItem>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import Circle from "vue-material-design-icons/Circle.vue"
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue"

    import {State} from "@kestra-io/design-system"

    import {useExecutionsStore} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../../../../utils/toast"
    import action from "../../../../../models/action"
    import resource from "../../../../../models/resource"

    const props = defineProps({
        execution: {
            type: Object,
            required: true,
        },
    })

    const {t} = useI18n()
    const authStore = useAuthStore()
    const executionsStore = useExecutionsStore()
    const toast = useToast()

    const user = computed(() => authStore.user)

    const enabled = computed(() => {
        if (!(user.value && user.value.isAllowed(resource.EXECUTION, action.DELETE, props.execution.namespace))) {
            return false
        }

        return State.isKillable(props.execution.state.current)
    })

    function kill(isOnKillCascade: boolean) {
        toast.confirm(t("killed confirm", {id: props.execution.id}), () => {
            return executionsStore
                .kill({
                    id: props.execution.id,
                    isOnKillCascade: isOnKillCascade,
                })
                .then(() => {
                    toast.success(t("killed done"))
                })
        })
    }
</script>

<style scoped lang="scss">
    .kill-dropdown {
        width: 100%;
    }

    .m-dropdown-menu {
        width: fit-content !important;

        :deep(.kel-dropdown-menu__item:hover) {
            background-color: var(--ks-log-background-error) !important;
            color: var(--ks-content-error) !important;
        }
    }
</style>
