<template>
    <Teleport to="body">
        <Transition name="onboarding-success-fade">
            <div
                v-if="modelValue"
                class="onboarding-success-overlay"
                :class="{'without-backdrop': !props.backdrop}"
            >
                <div class="onboarding-success-card">
                    <h3>{{ $t("welcome_copilot.success_popup.title") }}</h3>
                    <p>{{ $t("welcome_copilot.success_popup.description") }}</p>

                    <div class="onboarding-success-card__actions">
                        <button
                            class="el-button"
                            type="button"
                            @click="goToTutorial"
                        >
                            {{ $t("welcome_copilot.success_popup.tutorial") }}
                        </button>
                        <router-link
                            class="el-button el-button--primary"
                            :to="successRoute"
                        >
                            {{ $t("welcome_copilot.success_popup.explore") }}
                        </router-link>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>

<script setup lang="ts">
    import {computed, nextTick} from "vue";
    import {useRoute, useRouter} from "vue-router";

    const props = withDefaults(defineProps<{
        modelValue: boolean;
        backdrop?: boolean;
    }>(), {
        backdrop: true,
    });
    const emit = defineEmits<{
        "update:modelValue": [boolean];
    }>();

    const route = useRoute();
    const router = useRouter();
    const tutorialRoute = computed(() => ({
        name: "flows/create",
        query: {onboarding: "guided", reset: "true"},
        params: {tenant: route.params.tenant},
    }));
    const successRoute = computed(() => ({
        name: "welcome/success",
        params: {tenant: route.params.tenant},
    }));

    async function goToTutorial() {
        if (!props.modelValue) {
            return;
        }

        emit("update:modelValue", false);
        await nextTick();
        await new Promise(resolve => window.requestAnimationFrame(() => resolve(undefined)));
        window.location.assign(router.resolve(tutorialRoute.value).href);
    }
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/_variables.scss";

    .onboarding-success-overlay {
        position: fixed;
        inset: 0;
        display: grid;
        place-items: center;
        z-index: 5000;
        overflow: hidden;
        background: rgba(15, 23, 42, 0.18);
    }

    .onboarding-success-overlay.without-backdrop {
        background: transparent;
        pointer-events: none;
    }

    .onboarding-success-overlay.without-backdrop .onboarding-success-card {
        pointer-events: auto;
    }

    .onboarding-success-card {
        position: relative;
        z-index: 1;
        width: min(100%, 360px);
        padding: 2rem 1.75rem 1.5rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: 14px;
        background: var(--ks-background-card);
        box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18);
        text-align: center;

        h3 {
            margin: 0 0 0.75rem;
            color: var(--ks-content-primary);
            font-size: $font-size-lg;
            font-weight: 700;
            line-height: 1.1;
        }

        p {
            margin: 0 0 1.5rem;
            color: var(--ks-content-secondary);
            font-size: $font-size-md;
            line-height: 1.5;
        }
    }

    .onboarding-success-card__actions {
        display: flex;
        justify-content: center;
        gap: 0.75rem;

        .el-button {
            min-width: 132px;
            margin: 0;
            text-decoration: none;
        }
    }

    .onboarding-success-fade-enter-active,
    .onboarding-success-fade-leave-active {
        transition: opacity 0.2s ease;
    }

    .onboarding-success-fade-enter-from,
    .onboarding-success-fade-leave-to {
        opacity: 0;
    }
</style>
