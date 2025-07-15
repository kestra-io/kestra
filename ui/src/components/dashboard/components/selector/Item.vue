<template>
    <el-dropdown-item class="w-100 p-2">
        <div class="col text-truncate">
            <small>{{ props.dashboard.title }}</small>
        </div>

        <div class="col-auto">
            <el-button v-if="props.dashboard.id !== 'default'" link :icon="Pencil" class="mx-0" @click.stop="props.edit(props.dashboard.id)" />
            <el-button v-if="props.dashboard.id !== 'default' && props.remove" link :icon="DeleteOutline" class="mx-0" @click.stop="props.remove(props.dashboard)" />
        </div>
    </el-dropdown-item>
</template>

<script setup lang="ts">
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";

    const props = defineProps({
        dashboard: {
            type: Object,
            default: () => ({id: undefined, title: undefined}),
        },
        edit: {type: Function, required: true},
        remove: {type: Function, default: undefined},
    });
</script>
