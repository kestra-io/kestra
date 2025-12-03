<template>
    <NavBar :breadcrumb="routeInfo.breadcrumb" :title="routeInfo.title">
        <template #title>
            <template v-if="isDeleted">
                <Alert class="text-warning me-2" />{{ $t('deleted_label') }}:&nbsp;
            </template>
            <Lock v-else-if="!isAllowedToEdit" class="me-2 gray-700" />
            <span :class="{'body-color': isDeleted}">
                {{ routeInfo.title }}
                <Badge v-if="routeInfo.beta" label="Beta" />
            </span>
        </template>
        <template #additional-right>
            <Actions />
        </template>
    </NavBar>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
    import Badge from "../global/Badge.vue";
    import Actions from "override/components/flows/Actions.vue";
    import NavBar from "../layout/TopNavBar.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {useAuthStore} from "override/stores/auth";
    import {useFlowStore} from "../../stores/flow";

    defineProps<{
        routeInfo: {
            title: string;
            breadcrumb: Array<any>;
            beta?: boolean;
        };
    }>();

    const flowStore = useFlowStore();
    const authStore = useAuthStore();

    const isDeleted = computed(() => flowStore.flow?.deleted || false);
    const isAllowedToEdit = computed(() =>
        authStore.user?.isAllowed(permission.FLOW, action.UPDATE, flowStore.flow?.namespace)
    );
</script>