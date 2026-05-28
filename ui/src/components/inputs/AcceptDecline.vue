<template>
    <transition name="accept-decline-fade">
        <div v-show="visible" class="accept-decline-bar" role="status" aria-live="polite">
            <div class="bar-content">
                <div class="left-slot" />
                <KsTooltip :content="$t('draft_available')" placement="top">
                    <div class="buttons">
                        <KsButton @click="emit('reject')">
                            {{ $t("reject") }}
                        </KsButton>
                        <KsButton type="primary" @click="emit('accept')">
                            {{ $t("accept") }}
                        </KsButton>
                    </div>
                </KsTooltip>
            </div>
        </div>
    </transition>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    const emit = defineEmits<{ accept: []; reject: [] }>()
    const props = defineProps({
        visible: {type: Boolean, default: true},
    })
    const visible = computed(() => props.visible)
</script>

<style scoped lang="scss">
.accept-decline-bar {
    position: relative;
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
        background: var(--ks-bg-input);
        padding: .75rem 1rem;
    }

    .left-slot {
        flex: 1;
    }

    .buttons {
        display: flex;
        gap: .5rem;
        align-items: center;
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

.kel-button {
    padding: 4px 12px;
}
</style>

