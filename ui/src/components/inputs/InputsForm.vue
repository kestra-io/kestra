<template>
    <template v-if="inputsList">
        <el-form-item
            v-for="input in inputsList || []"
            :key="input.id"
            :label="input.displayName ? input.displayName : input.id"
            :required="input.required !== false"
            :prop="input.id"
        >
            <!-- Radio Button Group Rendering -->
            <el-radio-group
                v-if="input.isRadio"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                class="w-100"
            >
                <el-radio
                    v-for="item in input.values"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-radio>
            </el-radio-group>

            <!-- Select Dropdown Rendering -->
            <el-select
                v-else-if="input.type === 'ENUM' || input.type === 'SELECT'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                :allow-create="input.allowCustomValue"
                filterable
            >
                <el-option
                    v-for="item in input.values"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>

            <!-- Other input types remain unchanged -->
            <!-- Additional code for handling other input types (STRING, BOOLEAN, etc.) goes here -->
        </el-form-item>
    </template>
    <el-alert type="info" :show-icon="true" :closable="false" v-else>
        {{ $t("no inputs") }}
    </el-alert>
</template>

<script>
// Additional script content as needed
</script>

<style scoped lang="scss">
.hint {
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}

.text-description {
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}
</style>
