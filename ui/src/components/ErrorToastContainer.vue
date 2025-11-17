<template>
    <el-button
        v-if="isFlowContext"
        @click="fixWithAi"
        class="el-button--small"
        size="small"
    >
        <AiIcon class="me-1" />
        <span>{{ $t("fix_with_ai") }}</span>
    </el-button>
    <span v-html="markdownRenderer" v-if="items.length === 0" />
    <ul>
        <li v-for="(item, index) in items" :key="index" class="font-monospace">
            <template v-if="item.path">
                At <code>{{ item.path }}</code>:
            </template>
            <span>{{ item.message }}</span>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue";
    import {useRoute} from "vue-router";
    import AiIcon from "vue-material-design-icons/Creation.vue";
    import * as Markdown from "../utils/markdown";
    import {useFlowStore} from "../stores/flow";

    interface ErrorItem {
        path?: string;
        message: string;
    }

    interface ErrorMessage {
        message?: string;
        title?: string;
        content?: {
            message: string;
        };
        response?: {
            status: number;
        };
    }

    interface Props {
        message: ErrorMessage;
        items: ErrorItem[];
        onClose?: (() => void) | null;
    }

    const props = withDefaults(defineProps<Props>(), {
        onClose: null
    });

    const route = useRoute();
    const flowStore = useFlowStore();
    const markdownRenderer = ref<string | undefined>(undefined);

    const isFlowContext = computed(() => {
        const routeName = route?.name;
        return routeName === "flows/update" || routeName === "flows/create";
    });

    const renderMarkdown = async (): Promise<string> => {
        if (props.message.response && props.message.response.status === 503) {
            return await Markdown.render("Server is temporarily unavailable. Please try again later.", {html: true});
        }
        return await Markdown.render(props.message.message || props.message.content?.message || "", {html: true});
    };

    const fixWithAi = async () => {
        const errorMessage = props.message.message || props.message.content?.message || "";
        const errorItems = props.items.map((item: ErrorItem) => {
            const path = item.path ? `At ${item.path}: ` : "";
            return path + item.message;
        }).join("\n");

        const fullErrorMessage = [errorMessage, errorItems].filter(Boolean).join("\n\n");
        const prompt = `Fix the following error in the flow:\n${fullErrorMessage}`;

        try {
            window.sessionStorage.setItem("kestra-ai-prompt", prompt);
        } catch (err) {
            console.warn("AI prompt not persisted to sessionStorage:", err);
        }

        // Close the notification
        if (props.onClose) {
            props.onClose();
        }

        flowStore.setOpenAiCopilot(true);
    };

    // Watch for changes in message
    watch(() => props.message, async () => {
        markdownRenderer.value = await renderMarkdown();
    }, {deep: true});

    onMounted(async () => {
        markdownRenderer.value = await renderMarkdown();
    });
</script>

<style scoped lang="scss">
    ul {
        margin: 1rem 0 0;
        padding: 0;
        list-style-type: none;
    }

    li {
        font-size: 0.8rem;
        margin-top: .5rem;

    }
</style>