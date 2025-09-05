<template>
    <el-alert
        v-if="duplicatedKeys?.length"
        :title="t('duplicate-pair', {label: t('key'), key: duplicatedKeys[0]})"
        type="error"
        show-icon
        :closable="false"
        class="mb-2"
    />
    <el-row v-for="(item, index) in currentValue" :key="index" :gutter="10" class="w-100" :data-testid="`task-dict-item-${item[0]}-${index}`">
        <el-col :span="6">
            <InputText
                :model-value="item[0]"
                @update:model-value="onKey(index, $event)"
                margin="m-0"
                placeholder="Key"
                :have-error="duplicatedKeys.includes(item[0])"
            />
        </el-col>
        <el-col :span="16">
            <component
                :is="schema.additionalProperties ? getTaskComponent(schema.additionalProperties) : TaskExpression"
                :model-value="item[1]"
                @update:model-value="onValueChange(index, $event)"
                :root="getKey(item[0])"
                :schema="schema.additionalProperties"
                :required="isRequired(item[0])"
                :definitions="definitions"
                :disabled
            />
        </el-col>
        <el-col :span="2" class="col align-self-center delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add v-if="!disabledAdding" @add="addItem()" />
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {DeleteOutline} from "../../utils/icons";

    import InputText from "../inputs/InputText.vue";
    import TaskExpression from "./TaskExpression.vue";
    import Add from "../Add.vue";
    import getTaskComponent from "./getTaskComponent";
    import debounce from "lodash/debounce";

    const {t} = useI18n();

    defineOptions({
        name: "TaskDict",
        inheritAttrs: false,
    });

    const props = defineProps({
        modelValue: {
            type: Object,
            default: () => ({}),
        },
        schema: {
            type: Object,
            required: true,
        },
        definitions: {
            type: Object,
            default: () => ({}),
        },
        root: {
            type: String,
            default: undefined,
        },
        disabled: {
            type: Boolean,
            default: false,
        },
    });

    const currentValue = ref<[string, any][]>([])

    // this flag will avoid updating the modelValue when the
    // change was initiated in the component itself
    const localEdit = ref(false);

    watch(
        () => props.modelValue,
        (newValue) => {
            if(localEdit.value) {
                return;
            }
            localEdit.value = false;
            if(newValue === undefined || newValue === null) {
                currentValue.value = [];
                return;
            }
            currentValue.value = Object.entries(newValue ?? {});
        },
        {
            immediate: true,
            deep: true
        },
    );

    const duplicatedKeys = computed(() => {
        return currentValue.value.map(pair => pair[0])
            .filter((key, index, self) =>
                self.indexOf(key) !== index
            );
    });

    const emitUpdate = debounce(function () {
        if(duplicatedKeys.value?.length > 0) {
            return;
        }
        localEdit.value = true;
        emit("update:modelValue", Object.fromEntries(currentValue.value.filter(pair => pair[0] !== "" && pair[1] !== undefined)));
    }, 200);

    const emit = defineEmits(["update:modelValue"]);

    function getKey(key: string) {
        return props.root ? `${props.root}.${key}` : key;
    }

    function isRequired(key: string) {
        return props.schema?.required?.includes(key);
    }

    function onKey(key: number, val: string) {
        currentValue.value[key][0] = val;
        emitUpdate()
    }

    function onValueChange(key: number, val: any) {
        currentValue.value[key][1] = val;
        emitUpdate()
    }

    function removeItem(index: number) {
        currentValue.value.splice(index, 1);
        emitUpdate()
    }

    function addItem() {
        currentValue.value.push(["", undefined]);
        emitUpdate()
    }

    const disabledAdding = computed(() => {
        return props.disabled || currentValue.value.at(-1)?.[0] === "" && currentValue.value.at(-1)?.[1] === undefined;
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
