<template>
    <div class="ai-trigger-box" v-if="show">
        <el-button 
            v-if="!opened"
            class="ai-trigger-button" 
            :icon="AiIcon" 
            @click="handleClick"
        >
            {{ $t("ai.flow.title") }}
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import AiIcon from "./AiIcon.vue";

    interface AITriggerButtonProps {
        show: boolean;
        opened: boolean;
    }

    interface AITriggerButtonEmits {
        (event: "click"): void;
    }

    withDefaults(defineProps<AITriggerButtonProps>(), {
        show: false,
        opened: false,
    });

    const emit = defineEmits<AITriggerButtonEmits>();

    function handleClick(): void {
        emit("click");
    }
</script>

<style scoped lang="scss">
.ai-trigger-box {
    --border-angle: 0turn;
    --main-bg: conic-gradient(from calc(var(--border-angle) + 50.37deg) at 50% 50%, #3991FF 0deg, #8C4BFF 124.62deg, #A396FF 205.96deg, #3991FF 299.42deg, #E0E0FF 342.69deg, #3991FF 360deg);
    --gradient-border: conic-gradient(from calc(var(--border-angle) + 50.37deg) at 50% 50%, #3991FF 0deg, #8C4BFF 124.62deg, #A396FF 205.96deg, #3991FF 299.42deg, #E0E0FF 342.69deg, #3991FF 360deg);
    
    display: flex;
    flex-direction: column;
    align-items: end;
    gap: 0.5rem;
    margin-top: 0.5rem;
    border: solid 1px transparent;
    border-radius: 3rem;
    background:
        var(--main-bg) padding-box,
        var(--gradient-border) border-box,
        var(--main-bg) border-box;

    background-position: center center;
    animation: bg-spin 3s linear infinite;

    @keyframes bg-spin {
        to {
            --border-angle: 1turn;
        }
    }

    .ai-trigger-button {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        background-color: var(--ks-button-background-secondary);
        color: var(--ks-content-primary);
        box-shadow: 0px 4px 4px 0px #00000040;
        font-size: 12px;
        font-weight: 700;
        border: none;
        border-radius: 3rem;
    }
}

@property --border-angle {
    syntax: "<angle>";
    inherits: true;
    initial-value: 0turn;
}
</style>
