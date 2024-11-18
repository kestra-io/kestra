<template>
    <template v-if="inputsList">
        <el-form-item
            v-for="input in inputsList || []"
            :key="input.id"
            :label="input.displayName ? input.displayName : input.id"
            :required="input.required !== false"
            :prop="input.id"
        >
            <editor
                v-if="input.type === 'STRING' || input.type === 'URI' || input.type === 'EMAIL'"
                :full-height="false"
                :input="true"
                :navbar="false"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                @confirm="onSubmit"
            />
            <el-select
                v-if="input.type === 'SELECT'"
                :model-value="inputs[input.id]"
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
            <el-radio-group
                v-if="input.type === 'ENUM'"
                :model-value="inputs[input.id]"
                @update:model-value="onChange"
                class="w-100"
            >
                <el-radio
                    v-for="item in input.values"
                    :key="item"
                    :label="item"
                >
                    {{ item }}
                </el-radio>
            </el-radio-group>
            <el-select
                v-if="input.type === 'MULTISELECT'"
                :full-height="false"
                :input="true"
                :navbar="false"
                v-model="multiSelectInputs[input.id]"
                @update:model-value="onMultiSelectChange(input.id, $event)"
                multiple
                filterable
                :allow-create="input.allowCustomValue"
            >
                <el-option
                    v-for="item in (input.values ?? input.options)"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>
            <el-input
                v-if="input.type === 'SECRET'"
                type="password"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                show-password
            />
            <span v-if="input.type === 'INT'">
                <el-input-number
                    :data-test-id="`input-form-${input.id}`"
                    v-model="inputs[input.id]"
                    @update:model-value="onChange"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="1"
                />
                <div v-if="input.min || input.max" class="hint">
                    {{ numberHint(input) }}
                </div>
            </span>
            <span v-if="input.type === 'FLOAT'">
                <el-input-number
                    :data-test-id="`input-form-${input.id}`"
                    v-model="inputs[input.id]"
                    @update:model-value="onChange"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="0.001"
                />
                <div v-if="input.min || input.max" class="hint">
                    {{ numberHint(input) }}
                </div>
            </span>
            <el-radio-group
                v-if="input.type === 'BOOLEAN'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                class="w-100"
            >
                <el-radio-button :label="$t('true')" :value="true" />
                <el-radio-button :label="$t('false')" :value="false" />
                <el-radio-button :label="$t('undefined')" :value="undefined" />
            </el-radio-group>
            <el-date-picker
                v-if="input.type === 'DATETIME'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="datetime"
            />
            <el-date-picker
                v-if="input.type === 'DATE'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="date"
            />
            <el-time-picker
                v-if="input.type === 'TIME'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="time"
            />
            <div v-if="input.type === 'FILE'" class="el-input el-input-file">
                <div class="el-input__wrapper">
                    <input
                        :data-test-id="`input-form-${input.id}`"
                        :id="input.id + '-file'"
                        class="el-input__inner"
                        type="file"
                        @change="onFileChange(input, $event)"
                        autocomplete="off"
                        :style="{ display: typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///') ? 'none' : '' }"
                    />
                    <label
                        v-if="typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///')"
                        :for="input.id + '-file'"
                    >
                        Kestra Internal Storage File
                    </label>
                </div>
            </div>
            <editor
                v-if="input.type === 'JSON' || input.type === 'ARRAY'"
                :full-height="false"
                :input="true"
                :navbar="false"
                :data-test-id="`input-form-${input.id}`"
                lang="json"
                v-model="inputs[input.id]"
            />
            <editor
                v-if="input.type === 'YAML'"
                :full-height="false"
                :input="true"
                :navbar="false"
                :data-test-id="`input-form-${input.id}`"
                lang="yaml"
                :model-value="inputs[input.id]"
                @change="onYamlChange(input, $event)"
            />
            <duration-picker
                v-if="input.type === 'DURATION'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
            />
            <markdown
                v-if="input.description"
                :data-test-id="`input-form-${input.id}`"
                class="markdown-tooltip text-description"
                :source="input.description"
                font-size-var="font-size-xs"
            />
            <template v-if="executeClicked">
                <template v-for="err in input.errors ?? []" :key="err">
                    <el-text type="warning">
                        {{ err.message }}
                    </el-text>
                </template>
            </template>
        </el-form-item>
    </template>
    <el-alert v-else type="info" :show-icon="true" :closable="false">
        {{ $t("no inputs") }}
    </el-alert>
</template>
