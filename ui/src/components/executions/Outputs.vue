<template>
    <el-dropdown-item
        :icon="LocationExit"
        :disabled="!outputs || outputs.length === 0"
        @click="isOpen = !isOpen"
    >
        {{ $t('outputs') }}
    </el-dropdown-item>

    <drawer
        :data-component="dataComponent"
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('outputs')"
    >
        <vars
            :execution="execution"
            class="table-unrounded mt-1"
            :data="outputs"
        />
    </drawer>
</template>

<script setup>
    import LocationExit from "vue-material-design-icons/LocationExit.vue";
</script>

<script>
    import Vars from "../executions/Vars.vue";
    import Drawer from "../Drawer.vue";
    import BaseComponents from "../../components/BaseComponents.vue"

    export default {
        extends: {BaseComponents},
        components: {
            Vars,
            Drawer,
        },
        props: {
            outputs: {
                type: Object,
                required: false,
                default: () => {}
            },
            execution: {
                type: Object,
                required: true
            }
        },
        data() {
            return {
                isOpen: false,
            };
        },
    };
</script>
