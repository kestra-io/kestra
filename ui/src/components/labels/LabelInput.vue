<template>
    <div
        class="d-flex w-100 mb-2"
        v-for="(label, index) in locals"
        :key="index"
    >
        <div class="flex-grow-1 d-flex align-items-center">
            <el-input
                class="form-control me-2"
                :placeholder="$t('key')"
                v-model="label.key"
                :disabled="localExisting.includes(label.key || '')"
                @update:model-value="update(index, $event, 'key')"
            />
            <el-input
                class="form-control me-2"
                :placeholder="$t('value')"
                v-model="label.value"
                @update:model-value="update(index, $event, 'value')"
            />
        </div>
        <div class="flex-shrink-1">
            <el-button-group class="d-flex">
                <el-button :icon="Plus" @click="addItem" />
                <el-button :icon="Minus" @click="removeItem(index)" />
            </el-button-group>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted} from "vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";

    interface Label {
        key: string | null;
        value: string | null;
    }

    const props = defineProps<{
        labels: Label[];
        existingLabels?: Label[];
    }>();

    const emit = defineEmits<{
        (e: "update:labels", value: Label[]): void;
    }>();

    const locals = ref<Label[]>([]);
    const localExisting = ref<string[]>([]);

    const addItem = () => {
        locals.value.push({key: null, value: null});
        emit("update:labels", locals.value);
    };

    const removeItem = (index: number) => {
        locals.value.splice(index, 1);
        if (locals.value.length === 0) {
            addItem();
        }
        emit("update:labels", locals.value);
    };

    const update = (index: number, value: string | null, prop: keyof Label) => {
        locals.value[index][prop] = value;
        emit("update:labels", locals.value);
    };

    onMounted(() => {
        if (props.labels.length === 0) {
            addItem();
        } else {
            locals.value = props.labels;
            if (locals.value.length === 0) {
                addItem();
            }
        }

        localExisting.value = props.existingLabels?.map((label) => label.key ?? "") || [];
    });
</script>
