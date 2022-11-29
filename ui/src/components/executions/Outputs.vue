<template>
    <el-dropdown-item
        :icon="icon.LocationExit"
        :disabled="!outputs || outputs.length === 0"
        @click="isOpen = !isOpen"
    >
        {{ $t('outputs') }}
    </el-dropdown-item>

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('outputs')"
        destroy-on-close
        :append-to-body="true"
        size="50%"
        direction="ltr"
    >
        <vars
            :title="$t('outputs')"
            :execution="execution"
            class="table-unrounded mt-1"
            :data="outputs"
        />
    </el-drawer>
</template>

<script>
    import {shallowRef} from "vue";
    import Kicon from "../Kicon";
    import Vars from "../executions/Vars";
    import Timer from "vue-material-design-icons/Timer";
    import Counter from "vue-material-design-icons/Numeric";
    import LocationExit from "vue-material-design-icons/LocationExit.vue";

    export default {
        components: {
            Kicon,
            Timer,
            Counter,
            Vars,
        },
        props: {
            outputs: {
                type: Object,
                required: false,
            },
            execution: {
                type: Object,
                required: true
            }
        },
        data() {
            return {
                isOpen: false,
                icon: {LocationExit: shallowRef(LocationExit)}
            };
        },
    };
</script>
