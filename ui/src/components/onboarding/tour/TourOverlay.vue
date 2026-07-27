<template>
    <TourFinale
        v-if="showFinale"
        @restart="restartTour"
        @close="closeFinale"
    />

    <div v-else-if="tourStore.isGuidedActive" class="tour-overlay" aria-live="polite">
        <div
            ref="cardEl"
            class="guide-card"
            :class="{'is-left': !showIntro && scene.placement === 'left'}"
            :style="cardInlineStyle"
            @mousedown="onCardMouseDown"
        >
            <!-- The invitation lives in the same card as the rest of the tour, rather than behind a
                 full-screen dialog the user has to dismiss before seeing anything. -->
            <template v-if="showIntro">
                <div class="guide-top">
                    <span class="guide-step">{{ t("onboarding.tour.intro.kicker") }}</span>
                    <KsButton link class="guide-skip" @click="skipTour">
                        {{ t("onboarding.tour.intro.skip") }}
                    </KsButton>
                </div>

                <h3 class="guide-title">
                    {{ t("onboarding.tour.intro.title") }}
                </h3>
                <div class="guide-body">
                    {{ t("onboarding.tour.intro.body") }}
                </div>

                <!-- What is about to happen, so the card does not read as the first step of
                     something already running. Not numbered: the steps are counted in the card
                     header, and these are the groups they are shown in. -->
                <ul class="guide-plan">
                    <li v-for="group in TOUR_STEP_GROUPS" :key="group.step">
                        {{ t(`onboarding.tour.steps.${group.step}`) }}
                    </li>
                </ul>

                <p class="guide-note">
                    {{ t("onboarding.tour.intro.note") }}
                </p>

                <div class="guide-actions">
                    <span class="guide-spacer" />
                    <KsButton type="primary" @click="beginTour">
                        {{ t("onboarding.tour.intro.start") }}
                    </KsButton>
                </div>
            </template>

            <template v-else>
                <div class="guide-top">
                    <span class="guide-step">
                        {{ t("onboarding.tour.step_of", {current: sceneIndex + 1, total: TOUR_TOTAL_STEPS}) }}
                        <span class="guide-step-name">{{ t(`onboarding.tour.steps.${scene.step}`) }}</span>
                    </span>
                    <KsButton link class="guide-skip" @click="skipTour">
                        {{ t("onboarding.tour.actions.skip") }}
                    </KsButton>
                </div>

                <!-- One segment per step, split into ticks for the substeps it contains, so the
                     grouping is visible without listing a dozen steps. -->
                <div class="guide-progress">
                    <span
                        v-for="group in TOUR_STEP_GROUPS"
                        :key="group.step"
                        class="guide-progress-group"
                        :style="{flexGrow: group.scenes.length}"
                    >
                        <span
                            v-for="(id, index) in group.scenes"
                            :key="id"
                            class="guide-progress-tick"
                            :class="{filled: isTickFilled(group.step, index)}"
                        />
                    </span>
                </div>

                <div v-if="scene.milestone && isReady" class="milestone">
                    <CheckCircle :size="16" />
                    <span>{{ t(sceneKey("milestone")) }}</span>
                </div>

                <h3 class="guide-title">
                    {{ t(sceneKey("title")) }}
                </h3>
                <div class="guide-body" v-html="t(sceneKey('body'))" />

                <p v-if="scene.callout" class="guide-callout" v-html="t(sceneKey('callout'))" />

                <KsAlert v-if="error" type="error" :closable="false" class="guide-alert">
                    <template #title>
                        <span v-html="error" />
                    </template>
                </KsAlert>

                <div class="guide-actions">
                    <KsButton v-if="sceneIndex > 0" :disabled="isBusy" @click="back">
                        {{ t("onboarding.tour.actions.back") }}
                    </KsButton>
                    <span class="guide-spacer" />
                    <KsButton
                        v-if="scene.offersExit"
                        :disabled="isBusy || !isReady"
                        @click="finishTour"
                    >
                        {{ t("onboarding.tour.actions.finish_now") }}
                    </KsButton>
                    <KsButton
                        type="primary"
                        :disabled="isBusy || !isReady"
                        @click="next"
                    >
                        <span v-if="isBusy || !isReady" class="guide-running">
                            <span class="guide-spinner" />
                            {{ t("onboarding.tour.actions.running") }}
                        </span>
                        <span v-else>{{ nextLabel }}</span>
                    </KsButton>
                </div>
            </template>
        </div>

    </div>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"

    import TourFinale from "./TourFinale.vue"
    import {
        TOUR_SCENES,
        TOUR_SCENE_IDS,
        TOUR_STEP_GROUPS,
        TOUR_TOTAL_STEPS,
        TourSceneError,
        tourSceneIndex,
    } from "./tourScenes"
    import {useTourActions} from "./useTourActions"
    import {shouldShowWelcome} from "../../../utils/welcomeGuard"
    import {useProductTourStore} from "../../../stores/productTour"
    import {useMiscStore} from "override/stores/misc"
    import {useOnboardingAnalytics, type OnboardingTourEvent} from "../../../composables/useOnboardingAnalytics"
    import {useToast} from "../../../utils/toast"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const tourStore = useProductTourStore()
    const miscStore = useMiscStore()
    const actions = useTourActions()
    const {trackOnboarding} = useOnboardingAnalytics()
    const toast = useToast()

    /** `?tour=start` on any route starts the tour, which is what the left menu entry links to. */
    const consumeStartQuery = async () => {
        if (route.query.tour !== "start") {
            return false
        }
        const query = {...route.query}
        delete query.tour
        await router.replace({name: route.name ?? undefined, params: route.params, query})
        tourStore.startGuided()
        return true
    }

    // One answered check per session is enough: an instance does not become new again while the app
    // is open. A check that could not be answered is not counted, so it is tried again later.
    let autoStartChecked = false

    /**
     * The Copilot page is where a new instance opens, so the invitation is on screen there without
     * having to be asked for.
     *
     * Only on an instance without flows of its own, and only until the tour has been started once:
     * whoever skipped it keeps the left menu entry, but is not asked again.
     */
    const autoStartOnCopilot = async () => {
        if (autoStartChecked || route.name !== "ai") {
            return false
        }
        // Progress from another instance would otherwise count as "already offered here".
        tourStore.syncInstance(miscStore.configs?.uuid)
        if (tourStore.state.status !== "not_started" || tourStore.isDismissed) {
            return false
        }
        try {
            const isNewInstance = await shouldShowWelcome()
            autoStartChecked = true
            if (!isNewInstance) {
                return false
            }
        } catch {
            return false
        }
        tourStore.startGuided()
        return true
    }

    const isBusy = ref(false)
    const isReady = ref(false)
    const error = ref<string | null>(null)
    const showFinale = ref(false)

    const sceneIndex = computed(() => tourSceneIndex(tourStore.state.currentStepId))
    const scene = computed(() => TOUR_SCENES[sceneIndex.value])
    const context = computed(() => ({actions, store: tourStore}))

    const showIntro = computed(
        () => tourStore.isGuidedActive && !tourStore.state.tour.introSeen,
    )

    const sceneKey = (suffix: string) => `onboarding.tour.scenes.${scene.value.id}.${suffix}`

    /** A tick is filled for steps already done, and up to the current substep of this step. */
    const isTickFilled = (step: number, tickIndex: number) => {
        if (step < scene.value.step) {
            return true
        }
        if (step > scene.value.step) {
            return false
        }
        const group = TOUR_STEP_GROUPS.find((candidate) => candidate.step === step)
        return tickIndex <= (group?.scenes.indexOf(scene.value.id) ?? 0)
    }
    const nextLabel = computed(() => t(sceneKey("next")))

    /**
     * One event name per tour milestone, with the current step as the `action` property.
     *
     * `step_number` and `step_total` are what the card shows, so how far people get can be read
     * without knowing the order of the scenes.
     */
    const track = (event: OnboardingTourEvent, additional: Record<string, unknown> = {}) => {
        trackOnboarding({
            event,
            action: scene.value?.id,
            mode: tourStore.state.mode,
            additional: {
                step_group: scene.value?.step,
                step_number: sceneIndex.value + 1,
                step_total: TOUR_TOTAL_STEPS,
                ...additional,
            },
        })
    }

    /* ---------- highlighting the real UI ---------- */

    const highlighted = ref<HTMLElement[]>([])
    const HIGHLIGHT_CLASS = "onboarding-v2-highlight-static"
    let highlightTimer: number | null = null
    let highlightAttempts = 0

    const clearHighlight = () => {
        if (highlightTimer !== null) {
            window.clearTimeout(highlightTimer)
            highlightTimer = null
        }
        highlighted.value.forEach((element) => element.classList.remove(HIGHLIGHT_CLASS))
        highlighted.value = []
    }

    /**
     * The elements to highlight, from the first of the comma-separated selectors that matches.
     *
     * All of the matches, not only the first: some controls come in pairs, like the two revision
     * selectors of a diff, and a wrapper around them would leave the glow around half-empty columns.
     */
    const findTargets = (selector: string) => {
        for (const candidate of selector.split(",").map((value) => value.trim()).filter(Boolean)) {
            const visible = (Array.from(document.querySelectorAll(candidate)) as HTMLElement[])
                .filter((target) => {
                    const style = window.getComputedStyle(target)
                    if (style.display === "none" || style.visibility === "hidden") {
                        return false
                    }
                    const rect = target.getBoundingClientRect()
                    return rect.width > 0 && rect.height > 0
                })
            if (visible.length) {
                return visible
            }
        }
        return []
    }

    // Panels mount asynchronously after a route change, so retry for a couple of seconds.
    const applyHighlight = () => {
        clearHighlight()
        const selector = scene.value?.targetSelector
        if (!selector || !tourStore.isGuidedActive) {
            return
        }
        const targets = findTargets(selector)
        if (!targets.length) {
            if (highlightAttempts < 25) {
                highlightAttempts += 1
                highlightTimer = window.setTimeout(applyHighlight, 150)
            }
            return
        }
        highlightAttempts = 0
        highlighted.value = targets
        targets.forEach((target) => target.classList.add(HIGHLIGHT_CLASS))
    }

    /* ---------- dragging the card ---------- */

    /**
     * Everything in the card that reads as text rather than as chrome.
     *
     * The same list is used twice: dragging never starts here, and the cursor and selection are the
     * ones of ordinary text. Keep it in step with the `cursor: text` rule of the same name.
     */
    const SELECTABLE_TEXT = ".guide-title, .guide-body, .guide-plan, .guide-alert, .guide-callout, .milestone, code"

    const cardEl = ref<HTMLElement | null>(null)
    const dragOffset = ref({x: 0, y: 0})
    const cardInlineStyle = computed(() => ({
        transform: `translate(${dragOffset.value.x}px, ${dragOffset.value.y}px)`,
    }))

    const onCardMouseDown = (event: MouseEvent) => {
        if (event.button !== 0) {
            return
        }
        const target = event.target as HTMLElement | null
        if (target?.closest("button, a, input, textarea, select, [role='button'], .kel-button")) {
            return
        }
        // Text stays selectable: dragging starts from the card's chrome, not from its wording.
        if (target?.closest(SELECTABLE_TEXT)) {
            return
        }
        event.preventDefault()
        const start = {x: event.clientX, y: event.clientY}
        const startOffset = {...dragOffset.value}
        const startRect = cardEl.value?.getBoundingClientRect()
        const margin = 20

        const onMouseMove = (moveEvent: MouseEvent) => {
            if (!startRect) {
                return
            }
            const proposedLeft = startRect.left + (moveEvent.clientX - start.x)
            const proposedTop = startRect.top + (moveEvent.clientY - start.y)
            const clampedLeft = Math.max(margin, Math.min(proposedLeft, window.innerWidth - startRect.width - margin))
            const clampedTop = Math.max(margin, Math.min(proposedTop, window.innerHeight - startRect.height - margin))
            dragOffset.value = {
                x: startOffset.x + (clampedLeft - startRect.left),
                y: startOffset.y + (clampedTop - startRect.top),
            }
        }

        const onMouseUp = () => {
            window.removeEventListener("mousemove", onMouseMove)
            window.removeEventListener("mouseup", onMouseUp)
        }

        window.addEventListener("mousemove", onMouseMove)
        window.addEventListener("mouseup", onMouseUp)
    }

    /* ---------- confetti, for the milestones worth celebrating ---------- */

    const CONFETTI_COLORS = ["#9869f7", "#43f6b6", "#718bfe", "#fce070", "#F62E76", "#cdb6fb"]

    const confettiBurst = (count = 90) => {
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            return
        }
        for (let index = 0; index < count; index++) {
            const piece = document.createElement("span")
            piece.className = "tour-confetti-piece"
            piece.style.left = `${20 + Math.random() * 60}vw`
            piece.style.background = CONFETTI_COLORS[index % CONFETTI_COLORS.length]
            piece.style.animationDelay = `${Math.random() * 0.3}s`
            piece.style.animationDuration = `${1.6 + Math.random() * 1.2}s`
            document.body.appendChild(piece)
            window.setTimeout(() => piece.remove(), 3200)
        }
    }

    /* ---------- scene flow ---------- */

    /** Scene errors carry a translation key; anything else is shown as-is. */
    const describeError = (e: any) =>
        e instanceof TourSceneError ? t(e.key, e.params) : (e?.message ?? String(e))

    const runScene = async () => {
        isReady.value = false
        error.value = null
        clearHighlight()
        try {
            await scene.value?.enter?.(context.value)
            isReady.value = true
            applyHighlight()
            startPolling()
            if (scene.value?.confetti) {
                confettiBurst()
            }
        } catch (e: any) {
            error.value = describeError(e)
            isReady.value = true
            startPolling()
        }
    }

    const goTo = async (index: number) => {
        const id = TOUR_SCENE_IDS[index]
        if (!id) {
            return
        }
        tourStore.setStep(id)
        await runScene()
    }

    const next = async () => {
        if (isBusy.value || !isReady.value) {
            return
        }
        isBusy.value = true
        error.value = null
        track("tour_continued")
        try {
            await scene.value?.action?.(context.value)
            if (sceneIndex.value + 1 < TOUR_SCENES.length) {
                await goTo(sceneIndex.value + 1)
            } else {
                finishTour()
            }
        } catch (e: any) {
            error.value = describeError(e)
        } finally {
            isBusy.value = false
        }
    }

    const back = async () => {
        if (isBusy.value || sceneIndex.value === 0) {
            return
        }
        track("tour_continued", {direction: "back"})
        isBusy.value = true
        try {
            await goTo(sceneIndex.value - 1)
        } finally {
            isBusy.value = false
        }
    }

    const beginTour = async () => {
        tourStore.setTourState({introSeen: true})
        track("tour_started")
        await runScene()
    }

    const skipTour = () => {
        track("tour_closed")
        clearHighlight()
        stopPolling()
        actions.restoreEditorPanels()
        tourStore.skip()
        // Skipping leaves the user wherever the tour had taken them, so it says how to come back.
        toast.success(t("onboarding.tour.actions.skipped_hint"), t("onboarding.tour.menu"))
    }

    /**
     * Ends the tour, from the last step or from the early exit on a milestone.
     *
     * Both count as completed: someone who leaves at the end of a milestone has seen the tour
     * through, and the step it happened on is in the event.
     */
    const finishTour = () => {
        track("tour_completed")
        clearHighlight()
        stopPolling()
        actions.restoreEditorPanels()
        tourStore.complete()
        showFinale.value = true
    }

    const closeFinale = () => {
        showFinale.value = false
    }

    const restartTour = async () => {
        showFinale.value = false
        tourStore.startGuided()
        tourStore.setTourState({introSeen: true})
        track("tour_started", {restarted: true})
        await runScene()
    }

    // Re-highlight when the user navigates by hand in the middle of a scene.
    watch(() => scene.value?.id, () => applyHighlight())

    /**
     * The invitation appearing is the denominator of the start rate.
     *
     * Not immediate on purpose: a reload with the invitation still on screen is the same offer, and
     * counting it again would make the rate look worse than it is.
     */
    watch(showIntro, (visible) => {
        if (visible) {
            track("tour_offered")
        }
    })

    /**
     * Follow a step the user did themselves, in the real UI.
     *
     * Someone who presses the Copilot's own send button, runs the flow from the editor, opens a tab or
     * sends the test event from the trigger row has done exactly what the card asked for, so the tour
     * moves on instead of asking for its button as well. Never while the card's own button is working,
     * which is what `isBusy` is.
     */
    const followUserStep = async () => {
        if (isBusy.value || showIntro.value || !tourStore.isGuidedActive) {
            return
        }
        const completed = scene.value?.completedByUser?.({...context.value, route})
        if (!completed) {
            return
        }
        track("tour_continued", {done_by: "user"})
        await goTo(sceneIndex.value + 1)
    }

    watch(
        [() => route.fullPath, () => tourStore.state.tour],
        () => void followUserStep(),
        {deep: true},
    )

    /**
     * Some steps can be finished without the app noticing: an HTTP request sent with curl leaves
     * nothing on screen. Those scenes declare a `poll`, which runs while they are the current one.
     */
    let pollTimer: number | null = null

    const stopPolling = () => {
        if (pollTimer !== null) {
            window.clearInterval(pollTimer)
            pollTimer = null
        }
    }

    const startPolling = () => {
        stopPolling()
        const poll = scene.value?.poll
        if (!poll || !tourStore.isGuidedActive) {
            return
        }
        pollTimer = window.setInterval(() => {
            if (isBusy.value || showIntro.value) {
                return
            }
            // Whatever it records is picked up by the watcher above, which moves the tour on.
            void poll(context.value)
        }, 2500)
    }

    // Also on entry, from runScene: a tour resumed on this scene never changes scene.
    watch(() => tourStore.isGuidedActive, (active) => (active ? startPolling() : stopPolling()))

    watch(() => route.query.tour, () => void consumeStartQuery())

    // Reaching the Copilot page later in the session, without a reload, counts too.
    watch(() => route.name, () => void autoStartOnCopilot())

    onMounted(async () => {
        const started = (await consumeStartQuery()) || (await autoStartOnCopilot())
        if (!started && tourStore.isGuidedActive && tourStore.state.tour.introSeen) {
            // Resuming after a reload: re-enter the current scene so the app matches the card.
            await runScene()
        }
    })

    onBeforeUnmount(() => {
        clearHighlight()
        stopPolling()
    })
</script>

<style scoped lang="scss">
    .tour-overlay {
        position: fixed;
        inset: 0;
        pointer-events: none;
        z-index: 5000;
    }

    .guide-card {
        position: absolute;
        right: 3rem;
        bottom: 3rem;
        width: min(460px, calc(100vw - 6rem));
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-top: 3px solid var(--ks-btn-primary-bg-default);
        box-shadow: 0 18px 44px rgba(0, 0, 0, 0.35), 0 3px 10px rgba(0, 0, 0, 0.22);
        border-radius: 8px;
        padding: 1rem;
        pointer-events: auto;
        // Dragging is the default, except over the wording, which stays selectable.
        cursor: move;
        user-select: none;
    }

    // Over the left half of the page, next to the left menu, for scenes whose content sits in the
    // bottom right. Dragging still works from here.
    .guide-card.is-left {
        right: auto;
        left: calc(var(--menu-width, 268px) + 1rem);
    }

    // Same list as SELECTABLE_TEXT in the script above.
    .guide-title,
    .guide-body,
    .guide-plan,
    .guide-alert,
    .guide-callout,
    .milestone {
        cursor: text;
        user-select: text;
    }

    .guide-top {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        user-select: none;
    }

    .guide-step {
        display: flex;
        align-items: baseline;
        gap: 0.5rem;
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--ks-text-secondary);
    }

    // Not the link colour: it names the step rather than leading anywhere.
    .guide-step-name {
        color: var(--ks-text-primary);
        letter-spacing: 0.02em;
        text-transform: none;
    }

    .guide-note {
        margin: 0.75rem 0 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .guide-plan {
        margin: 0.75rem 0 0;
        padding-left: 1.25rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        line-height: 1.6;
    }

    // A step's body may list what it is describing, as an ordered list in its translation.
    .guide-body :deep(ol) {
        margin: 0.375rem 0;
        padding-left: 1.25rem;
    }

    .guide-progress {
        display: flex;
        gap: 6px;
        margin-top: 0.625rem;
    }

    // One segment per step, each split into a tick per substep.
    .guide-progress-group {
        display: flex;
        flex: 1;
        gap: 2px;
    }

    .guide-progress-tick {
        flex: 1;
        height: 3px;
        border-radius: 2px;
        background: var(--ks-border-default);
        transition: background 0.2s ease;

        &.filled {
            background: var(--ks-btn-primary-bg-default);
        }
    }

    .guide-dots {
        display: flex;
        gap: 4px;
    }

    .guide-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--ks-border-default);

        &.done {
            background: var(--ks-btn-primary-bg-default);
        }
    }

    .guide-skip {
        margin-left: auto;
    }

    .milestone {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-top: 0.75rem;
        padding: 0.5rem 0.75rem;
        border-radius: 6px;
        color: var(--ks-status-success);
        background: color-mix(in srgb, var(--ks-status-success) 12%, transparent);
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
    }

    .guide-title {
        margin: 0.75rem 0 0.25rem;
        font-size: var(--ks-font-size-lg);
    }

    .guide-body {
        margin-bottom: 0.75rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        line-height: 1.5;
    }

    .guide-alert {
        margin-bottom: 0.75rem;
    }

    // A KsAlert of type info renders dim text on a dim background, which is hard to read in a small
    // card, so the callout is a plain block with primary text and an accent edge.
    .guide-callout {
        margin: 0 0 0.75rem;
        padding: 0.625rem 0.75rem;
        border-left: 3px solid var(--ks-btn-primary-bg-default);
        border-radius: var(--ks-radius-sm);
        background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 12%, transparent);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        line-height: 1.5;
        cursor: text;
        user-select: text;
    }

    .guide-actions {
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .guide-spacer {
        flex: 1;
    }

    .guide-running {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
    }

    .guide-spinner {
        width: 12px;
        height: 12px;
        border: 2px solid rgba(255, 255, 255, 0.35);
        border-top-color: #fff;
        border-radius: 50%;
        animation: tourSpin 0.7s linear infinite;
    }

    @keyframes tourSpin {
        to {
            transform: rotate(360deg);
        }
    }

    /* Highlight applied to real controls in the app, so it has to leave the scoped tree. */
    :global(.onboarding-v2-highlight-static) {
        --onboarding-static-color: var(--ks-btn-primary-bg-default);
        box-shadow:
            0 0 16px 2px color-mix(in srgb, var(--onboarding-static-color) 36%, transparent),
            0 0 34px 10px color-mix(in srgb, var(--onboarding-static-color) 20%, transparent);
        border-radius: 10px;
        transition: box-shadow 0.2s ease;
    }

    :global(html.dark .onboarding-v2-highlight-static) {
        --onboarding-static-color: color-mix(in srgb, var(--ks-btn-primary-bg-default) 70%, white 30%);
        box-shadow:
            0 0 18px 3px color-mix(in srgb, var(--onboarding-static-color) 48%, transparent),
            0 0 40px 12px color-mix(in srgb, var(--onboarding-static-color) 24%, transparent);
    }

    :global(.tour-confetti-piece) {
        position: fixed;
        top: -12px;
        width: 8px;
        height: 14px;
        border-radius: 2px;
        pointer-events: none;
        z-index: 6000;
        animation-name: tourConfettiFall;
        animation-timing-function: linear;
        animation-fill-mode: forwards;
    }

    @keyframes tourConfettiFall {
        to {
            transform: translateY(105vh) rotate(540deg);
            opacity: 0;
        }
    }
</style>
