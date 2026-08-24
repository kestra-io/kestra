<template>
    <el-dropdown-item class="w-100 p-2">
        <div class="col text-truncate">
            <small>{{ props.dashboard.title }}</small>
        </div>

        <div class="col-auto">
            <el-button v-if="props.dashboard.id !== 'default' && canEdit" link :icon="Pencil" class="mx-0" @click.stop="props.edit(props.dashboard.id)" />
            <el-button v-if="props.dashboard.id !== 'default' && props.remove && canDelete" link :icon="DeleteOutline" class="mx-0" @click.stop="props.remove(props.dashboard)" />
        </div>
    </el-dropdown-item>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import {useAuthStore} from "override/stores/auth";
    import permission from "../../../../models/permission";
    import action from "../../../../models/action";

    const authStore = useAuthStore();

    const props = defineProps({
        dashboard: {
            type: Object,
            default: () => ({id: undefined, title: undefined}),
        },
        edit: {type: Function, required: true},
        remove: {type: Function, default: undefined},
    });

    const canEdit = computed(() => authStore.user?.isAllowed(permission.DASHBOARD, action.UPDATE, "*"));
    const canDelete = computed(() => authStore.user?.isAllowed(permission.DASHBOARD, action.DELETE, "*"));
</script>
