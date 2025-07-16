<template>
    <el-card>
        <template #header>
            <div class="d-flex justify-content-between">
                <span class="d-inline-flex title align-items-center">
                    <AiIcon /><span>{{ t("ai.flow.title") }}</span>
                </span>
                <el-button class="border-0" size="small" :icon="Close" @click.stop="emit('close')" />
            </div>
        </template>
        <el-input
            ref="promptInput"
            type="textarea"
            :placeholder="t('ai.flow.prompt_placeholder')"
            v-model="prompt"
            @keydown.exact.ctrl.enter.prevent="submitPrompt"
        />
        <template #footer>
            <div class="d-flex justify-content-between">
                <el-text class="text-tertiary" size="small">
                    ALT / ⌥ + K {{ t("to toggle") }}
                </el-text>
                <div class="d-flex flex-column align-items-end gap-3">
                    <el-text v-if="error !== undefined" type="danger" size="default" class="me-auto">
                        {{ error }}
                    </el-text>
                    <div v-if="waitingForReply" class="d-flex loading-text">
                        <div v-loading="true" />
                        <span>{{ t('ai.flow.generating') }}</span>
                    </div>
                    <el-button
                        v-else
                        type="primary"
                        :icon="KeyboardReturn"
                        :disabled="prompt.length === 0"
                        @click="submitPrompt"
                    >
                        {{ t('submit') }}
                    </el-button>
                </div>
            </div>
        </template>
    </el-card>
</template>

<script setup lang="ts">
    import {getCurrentInstance, onMounted, ref, watch} from "vue";
    import Close from "vue-material-design-icons/Close.vue";
    import KeyboardReturn from "vue-material-design-icons/KeyboardReturn.vue";
    import AiIcon from "./AiIcon.vue";
    import {useStore} from "vuex";

    const t = getCurrentInstance()!.appContext.config.globalProperties.$t;
    const store = useStore();
    const emit = defineEmits<{
        close: [];
        generatedYaml: [string];
    }>();

    const promptInput = ref<HTMLInputElement>();

    onMounted(() => {
        promptInput.value?.focus();
    })

    const prompt = ref(sessionStorage.getItem("kestra-ai-prompt") ?? "");
    const waitingForReply = ref(false);

    watch(prompt, (newValue) => {
        sessionStorage.setItem("kestra-ai-prompt", newValue);
    });

    const props = defineProps<{
        flow: string
    }>();

    const error = ref<string | undefined>(undefined);

    async function submitPrompt() {
        error.value = undefined;
        waitingForReply.value = true;

        let aiResponse;
        try {
            aiResponse = await store.dispatch("ai/generateFlow", {
                userPrompt: prompt.value,
                flowYaml: props.flow
            }) as string;
            emit("generatedYaml", aiResponse);
        } catch (e: any) {
            error.value = e.response?.data?.message as string ?? e;
        }

        waitingForReply.value = false;
    }
</script>

<style scoped lang="scss">
    :deep(.el-card__header) {
        font-size: 12px;
        line-height: 1;
        border-bottom: none;

        .title :not(:first-child) {
            margin-left: 6px;
        }
    }

    :deep(.el-card__footer) {
        border-top: none;
    }

    .loading-text {
        :first-child {
            width: 20px;
            height: 20px;
            --el-loading-spinner-size: 20px;
        }

        :not(:first-child) {
            margin-left: 6px;
        }
    }
</style>
