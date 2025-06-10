<template>
    <el-select
        :model-value="selectedInput?.type"
        @update:model-value="onChangeType"
        class="mb-3"
    >
        <el-option
            v-for="(input, index) in inputsType"
            :key="index"
            :label="input.type"
            :value="input.type"
        />
    </el-select>
    <TaskObject
        v-loading="loading"
        name="root"
        :model-value="selectedInput"
        @update:model-value="updateSelected($event, selectedIndex)"
        :schema="inputSchema?.schema?.properties"
        :properties="inputSchema?.schema?.properties?.properties"
        :definitions="inputSchema?.schema?.definitions"
        metadata-inputs
    />

    <Save @click="update" what="input" class="w-100 mt-3" />
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, inject} from "vue";
    import {useStore} from "vuex";
    import TaskObject from "./tasks/TaskObject.vue";
    import Save from "../code/components/Save.vue";
    import {BREADCRUMB_INJECTION_KEY, PANEL_INJECTION_KEY} from "../code/injectionKeys";

    interface InputType {
        type: string;
        id?: string;
    }

    const props = withDefaults(defineProps<{
        inputs: InputType[];
        label: string;
        selectedIndex: number;
        required?: boolean;
        disabled?: boolean;
    }>(), {
        inputs: () => [],
        required: false,
        disabled: false
    });

    const store = useStore();

    const inputSchema = computed(() => store.state.plugin.inputSchema);
    const inputsType = computed(() => store.state.plugin.inputsType);

    const emit = defineEmits<{
        (e: "update:inputs", value?: InputType[]): void
    }>();

    const panel = inject(PANEL_INJECTION_KEY, ref());
    const breadcrumbs = inject(BREADCRUMB_INJECTION_KEY, ref([]));

    const newInputs = ref<InputType[]>([{type: "STRING"}]);
    const loading = ref(false);

    const loadSchema = async (type: string) => {
        loading.value = true;
        await store.dispatch("plugin/loadInputSchema", {type});
        loading.value = false;
    };

    onMounted(() => {
        loading.value = true;
        store.dispatch("plugin/loadInputsType")
            .then(() => loading.value = false);

        if(selectedInput.value.type) {
            loadSchema(selectedInput.value.type);
        } else {
            loadSchema("STRING");
        }
    });

    watch(() => props.inputs, (val) => {
        if (val?.length) {
            newInputs.value = props.inputs;
        }
    }, {
        immediate: true,
        deep: true
    });

    const selectedInput = computed(() => {
        return props.inputs[props.selectedIndex] ?? {type: "STRING"};
    });

    const update = () => {
        panel.value = undefined;
        breadcrumbs.value.pop();
        const value = newInputs.value.filter(input => input?.id);
        emit("update:inputs", value.length ? value : undefined);
    };

    const updateSelected = (value: InputType, index: number) => {
        if (index >= 0) {
            if (index >= 0) {
                newInputs.value[index] = value;
                emit("update:inputs", [...newInputs.value]);
            }
        }
    };

    const onChangeType = (type: string) => {
        // Resetting the selected input if the type changes, but keeping the ID if it exists
        const id = selectedInput.value?.id;
        const newInput = {...(id ? {id} : {}), type};

        newInputs.value[props.selectedIndex] = newInput;

        emit("update:inputs", [...newInputs.value]);
        loadSchema(type);
    };
</script>

<style scoped lang="scss">
@import "../../components/code/styles/code.scss";
</style>
