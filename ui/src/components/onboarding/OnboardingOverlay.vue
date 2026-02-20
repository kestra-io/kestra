<template>
    <div
        v-if="onboardingStore.isGuidedActive"
        class="onboarding-overlay"
        :class="{'cancel-confirm-open': isCancelConfirmOpen}"
        aria-live="polite"
    >
        <div ref="guideCardEl" class="guide-card" :style="cardInlineStyle" @mousedown="onCardMouseDown">
            <div class="header">
                <strong>{{ stepTitle }}</strong>
                <div class="header-meta">
                    <small>{{ stepIndex + 1 }} / {{ steps.length }}</small>
                </div>
            </div>
            <p class="description" v-html="stepDescription" />
            <div v-if="snippetMarkdown" class="snippet-wrap">
                <Markdown
                    :source="snippetMarkdown"
                    font-size-var="font-size-xs"
                    :showCopyButtons="snippetCopyEnabled"
                />
            </div>
            <el-alert
                v-if="externalActionNote"
                type="info"
                :closable="false"
                showIcon
                class="feedback"
            >
                <template #title>
                    <span class="feedback-note-title" v-html="externalActionNote" />
                </template>
            </el-alert>
            <el-alert
                v-if="feedback.message"
                :title="feedback.message"
                :type="feedback.level === 'error' ? 'error' : feedback.level === 'info' ? 'info' : 'warning'"
                :closable="false"
                showIcon
                class="feedback"
            />
            <div v-if="!isFinishStep" class="actions">
                <el-button @click="cancelTour">
                    {{ t("onboarding.actions.cancel_tutorial") }}
                </el-button>
                <div class="actions-right">
                    <span v-if="showStepCompleteBadge && isStepComplete && !isFinishStep" class="step-complete">
                        <CheckCircle :size="16" />
                        {{ t("onboarding.actions.complete") }}
                    </span>
                    <el-button v-if="showNextButton" type="primary" @click="nextStep">
                        {{ nextLabel }}
                    </el-button>
                </div>
            </div>
            <div v-else class="actions finish-footer">
                <div class="actions-right">
                    <el-button @click="goToBlueprints">
                        {{ t("onboarding.finish_actions.explore_blueprints") }}
                    </el-button>
                    <el-button type="primary" :icon="Plus" @click="goToCreateFlow">
                        {{ t("onboarding.finish_actions.create_flow") }}
                    </el-button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useI18n} from "vue-i18n";
    import {ElMessageBox} from "element-plus";
    import {useFlowStore} from "../../stores/flow";
    import {useOnboardingV2Store} from "../../stores/onboardingV2";
    import {FIRST_FLOW_GUIDE_STEPS, FIRST_FLOW_STEP_IDS} from "./guides/firstFlowGuide";
    import {useOnboardingAnalytics} from "../../composables/useOnboardingAnalytics";
    import Markdown from "../layout/Markdown.vue";
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
    import Plus from "vue-material-design-icons/Plus.vue";

    const route = useRoute();
    const router = useRouter();
    const {t} = useI18n();
    const flowStore = useFlowStore();
    const onboardingStore = useOnboardingV2Store();
    const {trackOnboarding} = useOnboardingAnalytics();

    const steps = FIRST_FLOW_GUIDE_STEPS;
    const highlightedElement = ref<HTMLElement | null>(null);
    const guideCardEl = ref<HTMLElement | null>(null);
    const cardStyle = ref<Record<string, string>>({});
    const dragOffset = ref({x: 0, y: 0});
    const userHasDraggedCard = ref(false);
    const attemptedNext = ref(false);
    const isCancelConfirmOpen = ref(false);
    const highlightRetryCount = ref(0);
    const lastTrackedSaveCount = ref(onboardingStore.state.saveCount);
    const lastTrackedExecutionCount = ref(onboardingStore.state.executionCount);
    const maxHighlightRetries = 25;
    const executeModalTriggerSelector = "#execute-flow-dialog [data-onboarding-target=\"flow-execute-confirm-button\"]";
    let highlightRetryTimer: number | null = null;
    let executeStepRecheckTimer: number | null = null;
    const cardInlineStyle = computed(() => ({
        ...cardStyle.value,
        transform: `translate(${dragOffset.value.x}px, ${dragOffset.value.y}px)`,
    }));

    const stepIndex = computed(() => {
        const index = FIRST_FLOW_STEP_IDS.indexOf(onboardingStore.state.currentStepId || "");
        return index >= 0 ? index : 0;
    });

    const currentStep = computed(() => steps[stepIndex.value]);
    const isFinishStep = computed(() => currentStep.value?.id === "finish");
    const isExecuteStep = computed(
        () =>
            onboardingStore.state.status === "in_progress" &&
            currentStep.value?.stepType === "action_execute",
    );
    const isActionStep = computed(() => currentStep.value?.stepType?.startsWith("action_"));
    const translateMaybe = (value?: string) => {
        if (!value) {
            return "";
        }
        return value.startsWith("onboarding.") ? t(value) : value;
    };
    const stepTitle = computed(() => translateMaybe(currentStep.value?.title));
    const stepDescription = computed(() => translateMaybe(currentStep.value?.description));
    const externalActionNote = computed(() => translateMaybe(currentStep.value?.actionNote));
    const showNextButton = computed(() => !externalActionNote.value);
    const snippetMarkdown = computed(() => {
        const snippet = currentStep.value?.snippet;
        if (!snippet) {
            return "";
        }

        const flowId = flowStore.flow?.id;
        const resolvedSnippet = snippet
            .replace(/^id:\s*my_flow$/m, flowId ? `id: ${flowId}` : "id: my_flow");

        return `\`\`\`yaml\n${resolvedSnippet}\n\`\`\``;
    });
    const snippetCopyEnabled = computed(() => currentStep.value?.snippetCopyEnabled ?? true);
    const nextLabel = computed(() => {
        if (isFinishStep.value) {
            return t("onboarding.actions.finish_tutorial");
        }
        return t("onboarding.actions.next");
    });
    const trackCurrentStepAction = (action: string, additional: Record<string, unknown> = {}) => {
        trackOnboarding({
            action,
            mode: onboardingStore.state.mode,
            stepId: currentStep.value?.id,
            stepType: currentStep.value?.stepType,
            additional,
        });
    };
    const appendHintAtBottom = (hint: string) => {
        const source = flowStore.flowYaml ?? "";
        if (!source || source.includes(hint)) {
            return;
        }

        const trimmed = source.replace(/\s+$/, "");
        flowStore.flowYaml = `${trimmed}\n\n${hint}\n`;
    };
    const stepHintsByStepId: Record<string, string> = {
        add_id: "onboarding.editor_hints.step_1",
        add_cron_trigger: "onboarding.editor_hints.step_5",
    };

    const clearHighlight = (resetPosition = false) => {
        if (highlightRetryTimer !== null) {
            window.clearTimeout(highlightRetryTimer);
            highlightRetryTimer = null;
        }
        if (executeStepRecheckTimer !== null) {
            window.clearTimeout(executeStepRecheckTimer);
            executeStepRecheckTimer = null;
        }
        highlightedElement.value?.classList.remove("onboarding-v2-highlight");
        highlightedElement.value?.classList.remove("onboarding-v2-highlight-static");
        highlightedElement.value?.classList.remove("onboarding-v2-highlight-pulse");
        highlightedElement.value = null;
        if (resetPosition) {
            cardStyle.value = {};
            dragOffset.value = {x: 0, y: 0};
        }
    };

    function scheduleExecuteStepRecheck() {
        if (executeStepRecheckTimer !== null) {
            return;
        }
        executeStepRecheckTimer = window.setTimeout(() => {
            executeStepRecheckTimer = null;
            if (!isExecuteStep.value || onboardingStore.state.status !== "in_progress") {
                return;
            }
            applyHighlight();
        }, 180);
    }

    function queryTargetByPriority(selector: string): HTMLElement | null {
        for (const candidate of selector.split(",").map((value) => value.trim()).filter(Boolean)) {
            const targets = Array.from(document.querySelectorAll(candidate)) as HTMLElement[];
            const visibleTarget = targets.find((target) => {
                const style = window.getComputedStyle(target);
                if (style.display === "none" || style.visibility === "hidden") {
                    return false;
                }

                const rect = target.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0;
            });

            if (visibleTarget) {
                return visibleTarget;
            }
        }

        return null;
    }

    const onCardMouseDown = (event: MouseEvent) => {
        if (event.button !== 0) {
            return;
        }
        const target = event.target as HTMLElement | null;
        if (
            target?.closest(
                "button, a, input, textarea, select, label, [role='button'], .el-button, .el-input, .el-select",
            )
        ) {
            return;
        }
        event.preventDefault();
        const start = {x: event.clientX, y: event.clientY};
        const startOffset = {...dragOffset.value};
        let moved = false;
        const startRect = guideCardEl.value?.getBoundingClientRect();
        const viewportMargin = 20;

        const onMouseMove = (moveEvent: MouseEvent) => {
            if (!startRect) {
                return;
            }
            const dx = moveEvent.clientX - start.x;
            const dy = moveEvent.clientY - start.y;
            const proposedLeft = startRect.left + dx;
            const proposedTop = startRect.top + dy;
            const minLeft = viewportMargin;
            const maxLeft = window.innerWidth - startRect.width - viewportMargin;
            const minTop = viewportMargin;
            const maxTop = window.innerHeight - startRect.height - viewportMargin;
            const clampedLeft = Math.max(minLeft, Math.min(proposedLeft, maxLeft));
            const clampedTop = Math.max(minTop, Math.min(proposedTop, maxTop));
            dragOffset.value = {
                x: startOffset.x + (clampedLeft - startRect.left),
                y: startOffset.y + (clampedTop - startRect.top),
            };
            moved = moved || dragOffset.value.x !== startOffset.x || dragOffset.value.y !== startOffset.y;
        };

        const onMouseUp = () => {
            if (moved) {
                userHasDraggedCard.value = true;
            }
            window.removeEventListener("mousemove", onMouseMove);
            window.removeEventListener("mouseup", onMouseUp);
        };

        window.addEventListener("mousemove", onMouseMove);
        window.addEventListener("mouseup", onMouseUp);
    };

    function applyHighlight() {
        if (onboardingStore.state.status !== "in_progress") {
            clearHighlight();
            return;
        }
        const selector = currentStep.value?.targetSelector;
        if (selector) {
            let target: HTMLElement | null;
            if (isExecuteStep.value) {
                target = queryTargetByPriority(executeModalTriggerSelector);
                if (!target) {
                    target = queryTargetByPriority(selector);
                    scheduleExecuteStepRecheck();
                }
            } else {
                target = queryTargetByPriority(selector);
            }
            if (!target) {
                if (highlightRetryCount.value < maxHighlightRetries) {
                    highlightRetryCount.value += 1;
                    highlightRetryTimer = window.setTimeout(() => {
                        applyHighlight();
                    }, 120);
                }
                return;
            }
            highlightRetryCount.value = 0;
            const isEditorWrapperTarget = target.id === "editorWrapper";
            if (!isEditorWrapperTarget) {
                if (highlightedElement.value !== target) {
                    highlightedElement.value?.classList.remove("onboarding-v2-highlight");
                    highlightedElement.value?.classList.remove("onboarding-v2-highlight-static");
                    highlightedElement.value?.classList.remove("onboarding-v2-highlight-pulse");
                    highlightedElement.value = target;
                }
                highlightedElement.value.classList.add("onboarding-v2-highlight");
                if (isActionStep.value) {
                    highlightedElement.value.classList.add("onboarding-v2-highlight-pulse");
                    highlightedElement.value.classList.remove("onboarding-v2-highlight-static");
                } else {
                    highlightedElement.value.classList.add("onboarding-v2-highlight-static");
                    highlightedElement.value.classList.remove("onboarding-v2-highlight-pulse");
                }
            } else if (highlightedElement.value) {
                highlightedElement.value.classList.remove("onboarding-v2-highlight");
                highlightedElement.value.classList.remove("onboarding-v2-highlight-static");
                highlightedElement.value.classList.remove("onboarding-v2-highlight-pulse");
                highlightedElement.value = null;
            }
        } else {
            if (highlightedElement.value) {
                highlightedElement.value.classList.remove("onboarding-v2-highlight");
                highlightedElement.value.classList.remove("onboarding-v2-highlight-static");
                highlightedElement.value.classList.remove("onboarding-v2-highlight-pulse");
                highlightedElement.value = null;
            }
            highlightRetryCount.value = 0;
        }

        if (userHasDraggedCard.value) {
            return;
        }

        if (currentStep.value?.overlayPosition) {
            const cardWidth = Math.min(475, window.innerWidth - 96);
            const minMargin = 32;
            const rightEdgeMargin = minMargin * 3;
            const estimatedCardHeight = 320;

            let left = minMargin;
            if (currentStep.value.overlayPosition.horizontal === "center") {
                left = (window.innerWidth - cardWidth) / 2;
            } else if (currentStep.value.overlayPosition.horizontal === "right") {
                left = window.innerWidth - cardWidth - rightEdgeMargin;
            }
            left = Math.max(minMargin, Math.min(left, window.innerWidth - cardWidth - minMargin));

            let top = minMargin;
            if (currentStep.value.overlayPosition.vertical === "middle") {
                top = (window.innerHeight - estimatedCardHeight) / 2;
            } else if (currentStep.value.overlayPosition.vertical === "bottom") {
                top = window.innerHeight - estimatedCardHeight - minMargin;
            }
            top = Math.max(minMargin, Math.min(top, window.innerHeight - estimatedCardHeight - minMargin));

            cardStyle.value = {
                top: `${top}px`,
                left: `${left}px`,
                right: "auto",
                bottom: "auto",
                width: `${cardWidth}px`,
            };
            return;
        }

        if (isExecuteStep.value) {
            const dialog = document.querySelector("#execute-flow-dialog .el-dialog") as HTMLElement | null;
            if (dialog) {
                const rect = dialog.getBoundingClientRect();
                const cardWidth = Math.min(475, window.innerWidth - 96);
                const gap = 24;
                const minMargin = 32;
                const left = Math.max(
                    minMargin,
                    Math.min(rect.left + (rect.width - cardWidth) / 2, window.innerWidth - cardWidth - minMargin),
                );
                cardStyle.value = {
                    top: `${Math.min(rect.bottom + gap, window.innerHeight - 320)}px`,
                    left: `${left}px`,
                    right: "auto",
                    bottom: "auto",
                    width: `${cardWidth}px`,
                };
                return;
            }
        }

        const estimatedCardHeight = 320;
        const minMargin = 32;
        const rightEdgeMargin = minMargin * 3;
        const top = Math.max(
            minMargin,
            Math.min((window.innerHeight - estimatedCardHeight) / 2, window.innerHeight - estimatedCardHeight - minMargin),
        );
        cardStyle.value = {
            top: `${top}px`,
            left: "auto",
            right: `${rightEdgeMargin}px`,
            bottom: "auto",
        };
    }

    const validationContext = computed(() => ({
        flowYaml: flowStore.flowYaml,
        routeName: route.name?.toString() ?? null,
        saveCount: onboardingStore.state.saveCount,
        executionCount: onboardingStore.state.executionCount,
    }));

    const validationResult = computed(() => currentStep.value?.validate(validationContext.value));
    const canProceed = computed(() => {
        if (!validationResult.value || validationResult.value.ok) {
            return true;
        }
        return validationResult.value.level === "warn" && Boolean(currentStep.value?.allowNextOnWarning);
    });
    const showStepCompleteBadge = computed(() => currentStep.value?.showCompletionBadge !== false);
    const isStepComplete = computed(() => canProceed.value);
    const validationVisibility = computed(() => {
        if (!currentStep.value) {
            return "always";
        }
        if (currentStep.value.validationVisibility) {
            return currentStep.value.validationVisibility;
        }
        return currentStep.value.stepType === "code_edit" ? "after_input" : "always";
    });
    const canShowValidation = computed(() => {
        if (!currentStep.value) {
            return false;
        }

        if (validationVisibility.value === "after_input") {
            return Boolean(flowStore.flowYaml?.trim());
        }

        return true;
    });
    const feedback = computed<{level?: "info" | "warn" | "error"; message: string}>(() => {
        if (!attemptedNext.value || !validationResult.value || canProceed.value || !canShowValidation.value) {
            return {message: ""};
        }
        const level =
            validationResult.value.level === "warn"
                ? "warn"
                : validationResult.value.level === "info"
                    ? "info"
                    : "error";
        return {
            level,
            message: translateMaybe(validationResult.value.message) || t("onboarding.validation.complete_step"),
        };
    });

    const goToStep = (index: number) => {
        const nextStep = steps[Math.max(0, Math.min(index, steps.length - 1))];
        onboardingStore.setStep(nextStep.id);
        attemptedNext.value = false;
    };

    const nextStep = () => {
        if (isFinishStep.value) {
            completeGuide();
            return;
        }

        if (!canProceed.value) {
            attemptedNext.value = true;
            trackOnboarding({
                action: "step_validation_failed",
                mode: onboardingStore.state.mode,
                stepId: currentStep.value?.id,
                stepType: currentStep.value?.stepType,
                validationMessage: validationResult.value?.message,
            });
            return;
        }

        trackCurrentStepAction("step_next_clicked");

        if (currentStep.value?.id === "add_id") {
            appendHintAtBottom(t("onboarding.editor_hints.step_2"));
        } else if (currentStep.value?.id === "add_namespace") {
            appendHintAtBottom(t("onboarding.editor_hints.step_3"));
        } else if (currentStep.value?.id === "add_input") {
            appendHintAtBottom(t("onboarding.editor_hints.step_4"));
        }

        goToStep(stepIndex.value + 1);
    };

    const completeGuide = () => {
        trackCurrentStepAction("tutorial_completed");
        onboardingStore.complete();
        onboardingStore.setEditorMode("normal");
    };

    const cancelTour = async () => {
        const shouldRestoreExecuteFocus = isExecuteStep.value;
        if (shouldRestoreExecuteFocus) {
            toggleExecuteFocusMode(false);
        }

        isCancelConfirmOpen.value = true;
        try {
            await ElMessageBox.confirm(
                t("onboarding.cancel_modal.description"),
                t("onboarding.cancel_modal.title"),
                {
                    confirmButtonText: t("onboarding.cancel_modal.confirm"),
                    cancelButtonText: t("onboarding.cancel_modal.keep"),
                    type: "warning",
                },
            );
        } catch {
            isCancelConfirmOpen.value = false;
            if (shouldRestoreExecuteFocus) {
                toggleExecuteFocusMode(true);
            }
            return;
        }
        isCancelConfirmOpen.value = false;
        trackCurrentStepAction("tutorial_canceled");
        onboardingStore.skip();
        onboardingStore.setEditorMode("normal");
        toggleExecuteFocusMode(false);
    };

    const goToBlueprints = () => {
        trackCurrentStepAction("finish_explore_blueprints_clicked");
        completeGuide();
        void router.push({name: "blueprints", params: {kind: "flow", tab: "all"}});
    };

    const goToCreateFlow = () => {
        trackCurrentStepAction("finish_create_flow_clicked");
        completeGuide();
        void router.push({name: "flows/create"});
    };

    const toggleExecuteFocusMode = (enabled: boolean) => {
        document.body.classList.toggle("onboarding-execute-focus", enabled);
    };

    watch(
        () => onboardingStore.state.currentStepId,
        (stepId, previousStepId) => {
            if (stepId && stepId !== previousStepId) {
                clearHighlight();
                cardStyle.value = {};
                dragOffset.value = {x: 0, y: 0};
                userHasDraggedCard.value = false;
            }
            if (onboardingStore.state.status !== "in_progress" || !stepId) {
                return;
            }
            trackCurrentStepAction("step_viewed");
        },
        {immediate: true},
    );

    watch(
        () => [
            onboardingStore.state.currentStepId,
            route.fullPath,
            onboardingStore.state.status,
            flowStore.flowYaml,
        ],
        () => {
            highlightRetryCount.value = 0;
            window.requestAnimationFrame(() => {
                applyHighlight();
            });
        },
        {immediate: true},
    );

    watch(isExecuteStep, (enabled) => {
        toggleExecuteFocusMode(enabled);
        if (!enabled && executeStepRecheckTimer !== null) {
            window.clearTimeout(executeStepRecheckTimer);
            executeStepRecheckTimer = null;
        }
    }, {immediate: true});

    watch(
        () => [
            onboardingStore.state.currentStepId,
            onboardingStore.state.saveCount,
            onboardingStore.state.executionCount,
            onboardingStore.state.status,
            route.fullPath,
        ],
        () => {
            if (onboardingStore.state.status !== "in_progress") {
                return;
            }

            if (currentStep.value?.shouldAutoAdvance?.(validationContext.value)) {
                trackCurrentStepAction("step_auto_advanced");
                goToStep(stepIndex.value + 1);
            }
        },
    );

    watch(
        () => onboardingStore.state.saveCount,
        (newValue, oldValue) => {
            if (onboardingStore.state.status !== "in_progress") {
                return;
            }
            if (newValue > (oldValue ?? lastTrackedSaveCount.value)) {
                trackCurrentStepAction("flow_saved_during_tutorial", {
                    saveCount: newValue,
                });
            }
            lastTrackedSaveCount.value = newValue;
        },
        {immediate: true},
    );

    watch(
        () => onboardingStore.state.executionCount,
        (newValue, oldValue) => {
            if (onboardingStore.state.status !== "in_progress") {
                return;
            }
            if (newValue > (oldValue ?? lastTrackedExecutionCount.value)) {
                trackCurrentStepAction("flow_executed_during_tutorial", {
                    executionCount: newValue,
                });
            }
            lastTrackedExecutionCount.value = newValue;
        },
        {immediate: true},
    );

    watch(
        () => [onboardingStore.state.currentStepId, flowStore.flowYaml],
        () => {
            const stepId = onboardingStore.state.currentStepId ?? "";
            const hintKey = stepHintsByStepId[stepId];
            const hint = hintKey ? t(hintKey) : "";
            if (!hint) {
                return;
            }
            appendHintAtBottom(hint);
        },
        {immediate: true},
    );

    onMounted(() => {
        applyHighlight();
    });

    onBeforeUnmount(() => {
        toggleExecuteFocusMode(false);
        clearHighlight(true);
        if (highlightRetryTimer !== null) {
            window.clearTimeout(highlightRetryTimer);
            highlightRetryTimer = null;
        }
        if (executeStepRecheckTimer !== null) {
            window.clearTimeout(executeStepRecheckTimer);
            executeStepRecheckTimer = null;
        }
    });
</script>

<style scoped lang="scss">
    .onboarding-overlay {
        position: fixed;
        inset: 0;
        pointer-events: none;
        z-index: 5000;
    }

    .onboarding-overlay .guide-card {
        position: absolute;
        right: 3rem;
        bottom: 3rem;
        top: auto;
        left: auto;
        width: min(475px, calc(100vw - 6rem));
        background: var(--ks-background-card);
        border: 1px solid var(--ks-border-primary);
        box-shadow: 0 18px 44px rgba(0, 0, 0, 0.35), 0 3px 10px rgba(0, 0, 0, 0.22);
        border-radius: 8px;
        padding: 1rem;
        pointer-events: auto;
        z-index: 5001;
        cursor: move;
    }

    .onboarding-overlay.cancel-confirm-open {
        z-index: 1500 !important;
    }

    .onboarding-overlay.cancel-confirm-open .guide-card {
        z-index: 1501 !important;
    }

    .onboarding-overlay .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.75rem;
        cursor: move;
        user-select: none;
    }

    .onboarding-overlay .header-meta {
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .onboarding-overlay .step-complete {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        color: var(--el-color-success);
        font-size: 0.78rem;
        font-weight: 600;
    }

    .onboarding-overlay .description {
        margin: 0.75rem 0;
        color: var(--ks-content-primary);
        font-size: 0.875rem;
        line-height: 1.45;
    }

    .onboarding-overlay .snippet-wrap {
        margin-bottom: 0.75rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: 6px;
        overflow: hidden;
    }

    .onboarding-overlay .feedback {
        margin-bottom: 0.75rem;
    }

    .onboarding-overlay .feedback :deep(.el-alert__title) {
        white-space: pre-line;
    }

    .onboarding-overlay .feedback :deep(.feedback-note-title) {
        white-space: pre-line;
    }

    .onboarding-overlay .actions {
        display: flex;
        justify-content: space-between;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.25rem;
    }
    .onboarding-overlay .actions-right {
        display: flex;
        align-items: center;
        gap: 1rem;
    }


    .onboarding-overlay .finish-footer {
        justify-content: flex-end;
    }

    .onboarding-overlay .finish-footer .actions-right {
        justify-content: flex-end;
        gap: 0.5rem;
    }

    .onboarding-overlay .finish-footer .actions-right :deep(.el-button + .el-button) {
        margin-left: 0;
    }

    :global(.onboarding-v2-highlight-static) {
        --onboarding-static-color: var(--el-color-primary);
        box-shadow:
            0 0 16px 2px color-mix(in srgb, var(--onboarding-static-color) 36%, transparent),
            0 0 34px 10px color-mix(in srgb, var(--onboarding-static-color) 20%, transparent);
        border-radius: 10px;
        transition: box-shadow 0.2s ease;
    }

    :global(html.dark .onboarding-v2-highlight-static) {
        --onboarding-static-color: color-mix(in srgb, var(--el-color-primary) 70%, white 30%);
        box-shadow:
            0 0 18px 3px color-mix(in srgb, var(--onboarding-static-color) 48%, transparent),
            0 0 40px 12px color-mix(in srgb, var(--onboarding-static-color) 24%, transparent);
    }

    :global(.onboarding-v2-highlight-pulse) {
        --onboarding-pulse-color: var(--el-color-primary);
        --onboarding-pulse-strong: 50%;
        --onboarding-pulse-soft: 30%;
        --onboarding-pulse-scale: 1.045;
        --onboarding-pulse-ring-1: 8px;
        --onboarding-pulse-ring-2: 18px;
    }

    :global(.onboarding-v2-highlight-pulse .el-button),
    :global(.onboarding-v2-highlight-pulse.el-button) {
        animation: onboardingButtonPulse 1s ease-in-out infinite alternate;
        will-change: transform, box-shadow;
    }

    :global(html.dark .onboarding-v2-highlight-pulse) {
        --onboarding-pulse-color: color-mix(in srgb, var(--el-color-primary) 70%, white 30%);
        --onboarding-pulse-strong: 52%;
        --onboarding-pulse-soft: 34%;
        --onboarding-pulse-scale: 1.04;
        --onboarding-pulse-ring-1: 10px;
        --onboarding-pulse-ring-2: 24px;
    }

    :global(body.onboarding-execute-focus) .onboarding-overlay {
        z-index: 2147483646;
    }

    :global(body.onboarding-execute-focus) .onboarding-overlay .guide-card {
        z-index: 2147483647;
    }

    :global(body.onboarding-execute-focus [data-onboarding-target="flow-execute-button"]) {
        position: relative;
        z-index: 6001;
    }

    :global(body.onboarding-execute-focus #execute-button) {
        position: relative;
        z-index: 6001;
    }

    @keyframes onboardingButtonPulse {
        from {
            transform: translateZ(0) scale(1);
            box-shadow:
                0 0 0 0 color-mix(in srgb, var(--onboarding-pulse-color) var(--onboarding-pulse-strong), transparent),
                0 0 0 0 color-mix(in srgb, var(--onboarding-pulse-color) var(--onboarding-pulse-soft), transparent);
        }
        to {
            transform: translateZ(0) scale(var(--onboarding-pulse-scale));
            box-shadow:
                0 0 0 var(--onboarding-pulse-ring-1) transparent,
                0 0 0 var(--onboarding-pulse-ring-2) transparent;
        }
    }
</style>
