<template>
    <div id="debug">
        <Editor
            v-model="expression"
            :shouldFocus="false"
            :navbar="false"
            input
            class="expression"
        />

        <div class="buttons">
            <el-button type="primary" :icon="Refresh" @click="onRender">
                {{ $t("eval.render") }}
            </el-button>
            <el-button
                :disabled="!result && !error"
                :icon="CloseCircleOutline"
                @click="clearAll"
            />
        </div>

        <template v-if="result">
            <VarValue v-if="isFile" :value="result.value" :execution />

            <Editor
                v-else
                v-model="result.value"
                :shouldFocus="false"
                :navbar="false"
                input
                readOnly
                :lang="result.type"
                class="result"
            />
        </template>

        <el-alert
            v-else-if="error"
            type="error"
            :title="error"
            showIcon
            :closable="false"
        />
    </div>
</template>

<script setup lang="ts">
    import {watch, ref, computed} from "vue";

    import Editor from "../../../../../inputs/Editor.vue";
    import VarValue from "../../../../VarValue.vue";

    import {Execution} from "../../../../../../stores/executions";

    import Refresh from "vue-material-design-icons/Refresh.vue";
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue";

    const props = defineProps<{
        property: "outputs" | "trigger";
        execution: Execution;
        path: string;
    }>();

    const result = ref<{ value: string; type: string } | undefined>(undefined);
    const error = ref<string | undefined>(undefined);

    const clearAll = () => {
        result.value = undefined;
        error.value = undefined;
    };

    const isFile = computed(() => {
        if (!result.value || typeof result.value.value !== "string") return false;

        const prefixes = ["kestra:///", "file://", "nsfile://"];
        return prefixes.some((prefix) => result.value!.value.startsWith(prefix));
    });

    const expression = ref<string>("");
    watch(
        () => props.path,
        (path?: string) => {
            result.value = undefined;
            expression.value = `{{ ${props.property}${path ? `.${path}` : ""} }}`;
        },
        {immediate: true},
    );

    const onRender = () => {
        if (!props.execution) return;

        result.value = undefined;
        error.value = undefined;

        const clean = expression.value
            .replace(/^\{\{\s*/, "")
            .replace(/\s*\}\}$/, "")
            .trim();

        if (clean === "outputs" || clean === "trigger") {
            result.value = {
                value: JSON.stringify(props.execution[props.property], null, 2),
                type: "json",
            };
        }

        if (!clean.startsWith("outputs.") && !clean.startsWith("trigger.")) {
            result.value = undefined;
            error.value = `Expression must start with "{{ ${props.property}. }}"`;
            return;
        }

        const parts = clean.substring(props.property.length + 1).split(".");
        let target: any = props.execution[props.property];

        for (const part of parts) {
            if (target && typeof target === "object" && part in target) {
                target = target[part];
            } else {
                result.value = undefined;
                error.value = `Property "${part}" does not exist on ${props.property}`;
                return;
            }
        }

        if (target && typeof target === "object") {
            result.value = {
                value: JSON.stringify(target, null, 2),
                type: "json",
            };
        } else {
            result.value = {value: String(target), type: "text"};
        }
    };
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

#debug {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: calc($spacer / 2) $spacer;
    border: 1px solid var(--el-border-color-light);

    :deep(.ks-editor) {
        &.expression {
            height: calc($spacer * 2);
            margin-bottom: $spacer;
        }

        &.result {
            height: calc($spacer * 10);
        }
    }

    .buttons {
        display: inline-flex;

        & :deep(.el-button) {
            width: 100%;
            margin-bottom: $spacer;
            padding: $spacer;
            font-size: $font-size-sm;
            overflow: hidden;

            span:not(i span) {
                display: block;
                min-width: 0;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }

        & :deep(.el-button:nth-of-type(2)) {
            width: calc($spacer * 4);
        }
    }
}
</style>
