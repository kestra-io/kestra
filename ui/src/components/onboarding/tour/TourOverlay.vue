<template>
    <component :is="variant.finale" v-model="showFinale" @restart="restartTour" />

    <div
        v-if="tourStore.isGuidedActive && !showFinale"
        class="tour-overlay"
        aria-live="polite"
    >
        <template v-if="spotlight">
            <template v-if="spotlight.scrim">
                <div class="tour-scrim" :style="spotlight.scrim.top" />
                <div class="tour-scrim" :style="spotlight.scrim.bottom" />
                <div class="tour-scrim" :style="spotlight.scrim.left" />
                <div class="tour-scrim" :style="spotlight.scrim.right" />
            </template>
            <div
                v-for="(ring, index) in spotlight.rings"
                :key="index"
                class="tour-ring"
                :style="ring"
            />
        </template>

        <div
            ref="cardEl"
            class="guide-card"
            :class="{'is-left': !showIntro && scene.placement === 'left'}"
            :style="cardInlineStyle"
            @mousedown="onCardMouseDown"
        >
            <template v-if="showIntro">
                <div class="guide-top">
                    <span class="guide-step">{{ $t(tk("intro.kicker")) }}</span>
                    <KsButton link class="guide-skip" @click="skipTour">
                        {{ $t(tk("intro.skip")) }}
                    </KsButton>
                </div>

                <h3 class="guide-title">
                    {{ $t(tk("intro.title")) }}
                </h3>
                <div class="guide-body">
                    {{ $t(tk("intro.body")) }}
                </div>

                <ul class="guide-plan">
                    <li v-for="group in stepGroups" :key="group.step">
                        {{ $t(tk(`steps.${group.step}`)) }}
                    </li>
                </ul>

                <p class="guide-note">
                    {{ $t(tk("intro.note")) }}
                </p>

                <div class="guide-actions">
                    <span class="guide-spacer" />
                    <KsButton type="primary" @click="beginTour">
                        {{ $t(tk("intro.start")) }}
                    </KsButton>
                </div>
            </template>

            <template v-else>
                <div class="guide-top">
                    <span class="guide-step">
                        {{ $t(tk("step_of"), {current: sceneIndex + 1, total: totalSteps}) }}
                        <span class="guide-step-name">{{ $t(tk(`steps.${scene.step}`)) }}</span>
                    </span>
                    <KsButton link class="guide-skip" @click="skipTour">
                        {{ $t(tk("actions.skip")) }}
                    </KsButton>
                </div>

                <div class="guide-progress">
                    <span
                        v-for="group in stepGroups"
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

                <KsTag
                    v-if="scene.milestone && isReady"
                    class="milestone"
                    type="success"
                    :icon="CheckCircle"
                    :label="$t(sceneKey('milestone'))"
                />

                <h3 class="guide-title">
                    {{ $t(sceneKey("title")) }}
                </h3>
                <div class="guide-body" v-html="$t(sceneKey('body'))" />

                <p
                    v-if="scene.callout"
                    class="guide-callout"
                    v-html="$t(sceneKey('callout'))"
                />

                <KsAlert
                    v-if="error"
                    type="error"
                    :closable="false"
                    class="guide-alert"
                >
                    <template #title>
                        <span v-if="error.isHtml" v-html="error.message" />
                        <span v-else>{{ error.message }}</span>
                    </template>
                </KsAlert>

                <div class="guide-actions">
                    <KsButton v-if="sceneIndex > 0" :disabled="isBusy" @click="back">
                        {{ $t(tk("actions.back")) }}
                    </KsButton>
                    <span class="guide-spacer" />
                    <KsButton
                        v-if="scene.offersExit"
                        :disabled="isWorking"
                        @click="finishTour"
                    >
                        {{ $t(tk("actions.finish_now")) }}
                    </KsButton>
                    <KsButton
                        type="primary"
                        :loading="isWorking"
                        :disabled="isWorking"
                        @click="next"
                    >
                        {{ nextLabel }}
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
    import {TourSceneError, sceneIdsOf, sceneIndexOf, stepGroupsOf} from "./tourScenes"
    import {useTourVariant} from "override/components/onboarding/tour/useTourVariant"
    import {useProductTourStore} from "../../../stores/productTour"
    import {useMiscStore} from "override/stores/misc"
    import {useOnboardingAnalytics, type OnboardingTourEvent} from "../../../composables/useOnboardingAnalytics"
    import {useToast} from "../../../utils/toast"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const tourStore = useProductTourStore()
    const miscStore = useMiscStore()

    // Resolved once: remount the overlay if the resolution can change under it.
    const variant = useTourVariant()
    const actions = variant.useActions()
    const scenes = variant.scenes
    const sceneIds = sceneIdsOf(scenes)
    const stepGroups = stepGroupsOf(scenes)
    const totalSteps = scenes.length

    const {trackOnboarding} = useOnboardingAnalytics({sceneIds, guideId: variant.id})
    const toast = useToast()

    const tk = (suffix: string) => `${variant.i18nPrefix}.${suffix}`

    const consumeStartQuery = async () => {
        if (route.query.tour !== "start") {
            return false
        }
        const query = {...route.query}
        delete query.tour
        await router.replace({name: route.name ?? undefined, params: route.params, query})
        tourStore.startGuided(variant)
        return true
    }

    const syncTourScope = () => {
        const uuid = miscStore.configs?.uuid
        if (uuid) {
            tourStore.syncScope([uuid, route.params.tenant ?? "", variant.id].join(":"))
        }
    }

    let autoStartChecked = false

    const autoStartOnEntryRoute = async () => {
        if (autoStartChecked || route.name !== variant.autoStartRoute) {
            return false
        }
        syncTourScope()
        if (tourStore.state.status !== "not_started" || tourStore.isDismissed) {
            return false
        }
        try {
            const isEligible = await variant.eligible()
            autoStartChecked = true
            if (!isEligible) {
                return false
            }
        } catch {
            return false
        }
        tourStore.startGuided(variant)
        return true
    }

    type TourError = {message: string; isHtml: boolean}

    const isBusy = ref(false)
    const isReady = ref(false)
    const error = ref<TourError | null>(null)
    const showFinale = ref(false)

    const isWorking = computed(() => isBusy.value || !isReady.value)

    const sceneIndex = computed(() => sceneIndexOf(scenes, tourStore.state.currentStepId))
    const scene = computed(() => scenes[sceneIndex.value])
    const context = computed(() => ({actions, store: tourStore}))

    const showIntro = computed(
        () => tourStore.isGuidedActive && !tourStore.state.tour.introSeen,
    )

    const sceneKey = (suffix: string) => tk(`scenes.${scene.value.id}.${suffix}`)

    const isTickFilled = (step: number, tickIndex: number) => {
        if (step < scene.value.step) {
            return true
        }
        if (step > scene.value.step) {
            return false
        }
        const group = stepGroups.find((candidate) => candidate.step === step)
        return tickIndex <= (group?.scenes.indexOf(scene.value.id) ?? 0)
    }
    const nextLabel = computed(() =>
        isWorking.value ? t(tk("actions.running")) : t(sceneKey("next")),
    )

    const track = (event: OnboardingTourEvent, additional: Record<string, unknown> = {}) => {
        trackOnboarding({
            event,
            action: scene.value?.id,
            mode: tourStore.state.mode,
            additional: {
                step_group: scene.value?.step,
                step_number: sceneIndex.value + 1,
                step_total: totalSteps,
                ...additional,
            },
        })
    }

    const highlighted = ref<HTMLElement[]>([])
    const HIGHLIGHT_CLASS = "onboarding-v2-highlight-static"
    let highlightTimer: number | null = null
    let highlightAttempts = 0

    const RING_PADDING = 6

    const spotlight = ref<{
        scrim: Record<"top" | "bottom" | "left" | "right", Record<string, string>> | null;
        rings: Record<string, string>[];
    } | null>(null)

    let spotlightFrame: number | null = null
    let lastSpotlightKey = ""
    let activeSelector = ""

    const px = (value: number) => `${Math.round(value)}px`

    const dialogOpen = () =>
        Array.from(document.querySelectorAll(".kel-overlay-dialog, .kel-overlay")).some((element) => {
            const rect = element.getBoundingClientRect()
            return rect.width > 0 && rect.height > 0
        })

    const hideSpotlight = () => {
        if (lastSpotlightKey !== "") {
            lastSpotlightKey = ""
            spotlight.value = null
        }
    }

    const trackSpotlight = () => {
        spotlightFrame = window.requestAnimationFrame(trackSpotlight)

        if (dialogOpen()) {
            hideSpotlight()
            return
        }

        const matches = activeSelector
            ? Array.from(document.querySelectorAll<HTMLElement>(activeSelector))
                .map((element) => ({element, rect: element.getBoundingClientRect()}))
                .filter(({rect}) => rect.width > 0 && rect.height > 0)
            : []

        const elements = matches.map((match) => match.element)
        if (elements.length !== highlighted.value.length
            || elements.some((element, index) => element !== highlighted.value[index])) {
            highlighted.value.forEach((element) => element.classList.remove(HIGHLIGHT_CLASS))
            elements.forEach((element) => element.classList.add(HIGHLIGHT_CLASS))
            highlighted.value = elements
        }

        const rects = matches.map((match) => match.rect)

        if (!rects.length) {
            hideSpotlight()
            return
        }

        const top = Math.max(0, Math.min(...rects.map((rect) => rect.top)) - RING_PADDING)
        const left = Math.max(0, Math.min(...rects.map((rect) => rect.left)) - RING_PADDING)
        const bottom = Math.min(window.innerHeight, Math.max(...rects.map((rect) => rect.bottom)) + RING_PADDING)
        const right = Math.min(window.innerWidth, Math.max(...rects.map((rect) => rect.right)) + RING_PADDING)

        const key = [top, left, bottom, right, rects.length].map(Math.round).join(":")
        if (key === lastSpotlightKey) {
            return
        }
        lastSpotlightKey = key

        spotlight.value = {
            scrim: scene.value?.dim === false ? null : {
                top: {top: "0", left: "0", right: "0", height: px(top)},
                bottom: {top: px(bottom), left: "0", right: "0", bottom: "0"},
                left: {top: px(top), left: "0", width: px(left), height: px(bottom - top)},
                right: {top: px(top), left: px(right), right: "0", height: px(bottom - top)},
            },
            rings: rects.map((rect) => ({
                top: px(rect.top - RING_PADDING),
                left: px(rect.left - RING_PADDING),
                width: px(rect.width + RING_PADDING * 2),
                height: px(rect.height + RING_PADDING * 2),
            })),
        }
    }

    const clearHighlight = () => {
        if (highlightTimer !== null) {
            window.clearTimeout(highlightTimer)
            highlightTimer = null
        }
        if (spotlightFrame !== null) {
            window.cancelAnimationFrame(spotlightFrame)
            spotlightFrame = null
        }
        lastSpotlightKey = ""
        activeSelector = ""
        spotlight.value = null
        highlighted.value.forEach((element) => element.classList.remove(HIGHLIGHT_CLASS))
        highlighted.value = []
    }

    const findTargets = (selector: string) => {
        const candidates = selector.split(",").map((value) => value.trim()).filter(Boolean)
        for (const [index, candidate] of candidates.entries()) {
            const visible = Array.from(document.querySelectorAll<HTMLElement>(candidate))
                .filter((target) => {
                    const style = window.getComputedStyle(target)
                    if (style.display === "none" || style.visibility === "hidden") {
                        return false
                    }
                    const rect = target.getBoundingClientRect()
                    return rect.width > 0 && rect.height > 0
                })
            if (visible.length) {
                return {elements: visible, rank: index, candidate}
            }
        }
        return {elements: [] as HTMLElement[], rank: -1, candidate: ""}
    }

    const applyHighlight = () => {
        clearHighlight()
        const selector = scene.value?.targetSelector
        if (!selector || !tourStore.isGuidedActive) {
            return
        }
        const {elements: targets, rank, candidate} = findTargets(selector)
        const keepLooking = rank !== 0 && highlightAttempts < 25
        if (keepLooking) {
            highlightAttempts += 1
            highlightTimer = window.setTimeout(applyHighlight, 150)
        } else {
            highlightAttempts = 0
        }
        if (!targets.length) {
            return
        }
        highlighted.value = targets
        activeSelector = candidate
        targets.forEach((target) => target.classList.add(HIGHLIGHT_CLASS))

        const rect = targets[0].getBoundingClientRect()
        if (rect.top < 0 || rect.bottom > window.innerHeight) {
            targets[0].scrollIntoView({block: "center", behavior: "smooth"})
        }

        trackSpotlight()
    }

    // Keep in sync with the `cursor: text` rule listing the same selectors in the style block.
    const SELECTABLE_TEXT = ".guide-title, .guide-body, .guide-plan, .guide-alert, .guide-callout, .milestone, code"

    const cardEl = ref<HTMLElement | null>(null)
    const dragOffset = ref({x: 0, y: 0})
    const cardInlineStyle = computed(() => ({
        transform: `translate(${dragOffset.value.x}px, ${dragOffset.value.y}px)`,
    }))

    let stopDrag: (() => void) | null = null

    const onCardMouseDown = (event: MouseEvent) => {
        if (event.button !== 0) {
            return
        }
        const target = event.target as HTMLElement | null
        if (target?.closest("button, a, input, textarea, select, [role='button'], .kel-button")) {
            return
        }
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
            stopDrag = null
        }
        stopDrag = onMouseUp

        window.addEventListener("mousemove", onMouseMove)
        window.addEventListener("mouseup", onMouseUp)
    }

    const CONFETTI_TOKENS = [
        "--ks-btn-primary-bg-default",
        "--ks-status-success",
        "--ks-status-info",
        "--ks-status-warning",
        "--ks-status-error",
        "--ks-btn-primary-bg-hover",
    ]

    const confettiBurst = (count = 90) => {
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            return
        }
        for (let index = 0; index < count; index++) {
            const piece = document.createElement("span")
            piece.className = "tour-confetti-piece"
            piece.style.left = `${20 + Math.random() * 60}vw`
            piece.style.background = `var(${CONFETTI_TOKENS[index % CONFETTI_TOKENS.length]})`
            piece.style.animationDelay = `${Math.random() * 0.3}s`
            piece.style.animationDuration = `${1.6 + Math.random() * 1.2}s`
            document.body.appendChild(piece)
            window.setTimeout(() => piece.remove(), 3200)
        }
    }

    const describeError = (e: unknown): TourError => {
        if (e instanceof TourSceneError) {
            return {message: t(e.key, e.params), isHtml: true}
        }
        return {message: (e as {message?: string} | null)?.message ?? String(e), isHtml: false}
    }

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
        } catch (e) {
            error.value = describeError(e)
            isReady.value = true
            startPolling()
        }
    }

    const goTo = async (index: number) => {
        const id = sceneIds[index]
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
            if (sceneIndex.value + 1 < scenes.length) {
                await goTo(sceneIndex.value + 1)
            } else {
                finishTour()
            }
        } catch (e) {
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
        variant.cleanup?.(actions)
        tourStore.skip()
        toast.success(t(tk("actions.skipped_hint")), t(tk("menu")))
    }

    const finishTour = () => {
        track("tour_completed")
        clearHighlight()
        stopPolling()
        variant.cleanup?.(actions)
        tourStore.complete()
        showFinale.value = true
    }

    const restartTour = async () => {
        showFinale.value = false
        tourStore.startGuided(variant)
        tourStore.setTourState({introSeen: true})
        track("tour_started", {restarted: true})
        await runScene()
    }

    watch(() => scene.value?.id, () => applyHighlight())

    watch(() => scene.value?.placement, () => (dragOffset.value = {x: 0, y: 0}))

    watch(showIntro, (visible) => {
        if (visible) {
            track("tour_offered")
        }
    })

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
        [() => route.fullPath, () => tourStore.state.tour, () => tourStore.state.data],
        () => void followUserStep(),
        {deep: true},
    )

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
            void poll(context.value)
        }, 2500)
    }

    watch(() => tourStore.isGuidedActive, (active) => (active ? startPolling() : stopPolling()))

    watch(() => route.query.tour, () => void consumeStartQuery())

    watch(() => route.name, () => void autoStartOnEntryRoute())

    watch(
        () => [miscStore.configs?.uuid, route.params.tenant],
        () => syncTourScope(),
        {immediate: true},
    )

    onMounted(async () => {
        const started = (await consumeStartQuery()) || (await autoStartOnEntryRoute())
        if (!started && tourStore.isGuidedActive && tourStore.state.tour.introSeen) {
            await runScene()
        }
    })

    onBeforeUnmount(() => {
        clearHighlight()
        stopPolling()
        stopDrag?.()
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
        right: var(--ks-spacing-8);
        bottom: var(--ks-spacing-8);
        width: min(460px, calc(100vw - var(--ks-spacing-16)));
        background: var(--ks-bg-surface);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-top: var(--ks-border-width-thick) solid var(--ks-btn-primary-bg-default);
        box-shadow: var(--ks-shadow-lg);
        border-radius: var(--ks-radius-base);
        padding: var(--ks-spacing-4);
        pointer-events: auto;
        cursor: move;
        user-select: none;
    }

    .guide-card.is-left {
        right: auto;
        left: calc(var(--menu-width) + var(--ks-spacing-4));
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
        gap: var(--ks-spacing-3);
        user-select: none;
    }

    .guide-step {
        display: flex;
        align-items: baseline;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--ks-text-secondary);
    }

    .guide-step-name {
        color: var(--ks-text-primary);
        letter-spacing: 0.02em;
        text-transform: none;
    }

    .guide-note {
        margin: var(--ks-spacing-3) 0 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .guide-plan {
        margin: var(--ks-spacing-3) 0 0;
        padding-left: var(--ks-spacing-5);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        line-height: var(--ks-line-height-loose);
    }

    .guide-body :deep(ol) {
        margin: var(--ks-spacing-1) 0;
        padding-left: var(--ks-spacing-5);
    }

    .guide-progress {
        display: flex;
        gap: var(--ks-spacing-1);
        margin-top: var(--ks-spacing-2);
    }

    .guide-progress-group {
        display: flex;
        flex: 1;
        gap: var(--ks-spacing-px);
    }

    .guide-progress-tick {
        flex: 1;
        height: 3px;
        border-radius: var(--ks-radius-xs);
        background: var(--ks-border-default);
        transition: background var(--ks-duration-base) var(--ks-ease-standard);

        &.filled {
            background: var(--ks-btn-primary-bg-default);
        }
    }

    .guide-skip {
        margin-left: auto;
    }

    .milestone {
        margin-top: var(--ks-spacing-3);
    }

    .guide-title {
        margin: var(--ks-spacing-3) 0 var(--ks-spacing-1);
        font-size: var(--ks-font-size-lg);
    }

    .guide-body {
        margin-bottom: var(--ks-spacing-3);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        line-height: var(--ks-line-height-base);
    }

    .guide-alert {
        margin-bottom: var(--ks-spacing-3);
    }

    .guide-callout {
        margin: 0 0 var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-left: var(--ks-border-width-thick) solid var(--ks-btn-primary-bg-default);
        border-radius: var(--ks-radius-sm);
        background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 12%, transparent);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        line-height: var(--ks-line-height-base);
        cursor: text;
        user-select: text;
    }

    .guide-actions {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .guide-spacer {
        flex: 1;
    }

    :global(.onboarding-v2-highlight-static) {
        --onboarding-static-color: var(--ks-btn-primary-bg-default);
        border-radius: var(--ks-radius-lg);
        box-shadow: 0 0 0 1px color-mix(in srgb, var(--onboarding-static-color) 45%, transparent);
        transition: box-shadow var(--ks-duration-base) var(--ks-ease-standard);
    }

    :global(html.dark .onboarding-v2-highlight-static) {
        --onboarding-static-color: color-mix(in srgb, var(--ks-btn-primary-bg-default) 70%, white 30%);
    }

    // Position is set every frame; a transition would make the ring trail behind its target.
    .tour-scrim {
        position: fixed;
        background: var(--kel-overlay-color-lighter);
        pointer-events: none;
    }

    .tour-ring {
        --tour-ring-color: color-mix(in srgb, var(--ks-btn-primary-bg-default) 75%, white 25%);
        position: fixed;
        border: var(--ks-border-width-base) solid var(--tour-ring-color);
        border-radius: var(--ks-radius-lg);
        pointer-events: none;
        animation: tourRingPulse 1.5s var(--ks-ease-out) 3 forwards;
    }

    @keyframes tourRingPulse {
        0% {
            box-shadow:
                0 0 0 0 color-mix(in srgb, var(--tour-ring-color) 55%, transparent),
                0 0 24px 6px color-mix(in srgb, var(--tour-ring-color) 45%, transparent);
        }
        70% {
            box-shadow:
                0 0 0 12px color-mix(in srgb, var(--tour-ring-color) 0%, transparent),
                0 0 34px 12px color-mix(in srgb, var(--tour-ring-color) 30%, transparent);
        }
        100% {
            box-shadow:
                0 0 0 0 color-mix(in srgb, var(--tour-ring-color) 0%, transparent),
                0 0 26px 8px color-mix(in srgb, var(--tour-ring-color) 32%, transparent);
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .tour-ring {
            animation: none;
            box-shadow: 0 0 24px 6px color-mix(in srgb, var(--tour-ring-color) 32%, transparent);
        }
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