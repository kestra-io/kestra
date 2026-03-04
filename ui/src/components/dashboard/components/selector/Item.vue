<template>
    <el-dropdown-item class="w-100 p-2">
        <div class="col text-truncate">
            <small>{{ props.dashboard.title }}</small>
        </div>

        <div class="col-auto">
            <el-button v-if="props.dashboard.id !== 'default'" link :icon="props.dashboard.isDefault ? Home : HomeOutline" class="mx-0" @click.stop="props.setAsDefault(props.dashboard.id)" title="set as default" />
            <el-button v-if="props.dashboard.id !== 'default'" link :icon="Pencil" class="mx-0" @click.stop="props.edit(props.dashboard.id)" title="edit" />
            <el-button v-if="props.dashboard.id !== 'default' && props.remove" link :icon="DeleteOutline" class="mx-0" @click.stop="props.remove(props.dashboard)" title="delete" />
        </div>
    </el-dropdown-item>
</template>

<script setup lang="ts">
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Home from "vue-material-design-icons/Home.vue";
    import HomeOutline from "vue-material-design-icons/HomeOutline.vue";

    const props = defineProps<{
        dashboard: {id: string, title: string, isDefault: boolean},
        setAsDefault: (id: string) => void,
        edit: (id: string) => void,
        remove?: (dashboard: {id: string, title: string}) => void}>()
</script>
