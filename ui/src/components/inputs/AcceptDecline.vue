<template>
    <transition name="accept-decline-fade">
        <div v-show="visible" class="accept-decline-bar" role="status" aria-live="polite">
            <div class="bar-content">
                <div class="left-slot" />
                <el-tooltip effect="light" :content="$t('draft_available')" placement="top">
                    <div class="buttons">
                        <el-button @click="emit('reject')">
                            {{ $t("reject") }}
                        </el-button>
                        <el-button type="primary" @click="emit('accept')">
                            {{ $t("accept") }}
                        </el-button>
                    </div>
                </el-tooltip>
            </div>
        </div>
    </transition>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    const emit = defineEmits<{ accept: []; reject: [] }>();
    const props = defineProps({
        visible: {type: Boolean, default: true}
    });
    const visible = computed(() => props.visible);
</script>

<style scoped lang="scss">
.accept-decline-bar {
    position: relative; // now flow inside parent footer-row
    width: 100%;
    z-index: 1200;
    display: flex;
    justify-content: center;
    pointer-events: auto;
    transition: transform 180ms cubic-bezier(.2,.8,.2,1), opacity 160ms ease;

    .bar-content {
        width: 100%;
        max-width: 100%;
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: rgba(30,32,42,0.95);
        padding: .75rem 1rem;
        box-shadow: 0 -4px 18px rgba(2,6,23,0.45);
    }

    .left-slot {
        flex: 1;
    }

    .buttons {
        display: flex;
        gap: .5rem;
        align-items: center;
    }

    html.light & .bar-content {
        background: rgba(18,18,18,0.9);
    }
}

.accept-decline-fade-enter-from {
    opacity: 0;
    transform: translateY(8px) scale(.98);
}
.accept-decline-fade-enter-active {
    transition: opacity 200ms ease, transform 220ms cubic-bezier(.2,.8,.2,1);
}
.accept-decline-fade-leave-to {
    opacity: 0;
    transform: translateY(10px) scale(.98);
}

.el-button {
    padding: 4px 12px;
}
</style>
