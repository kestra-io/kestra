<template>
    <Teleport to="body">
        <Transition name="save-execute-fade">
            <div
                v-if="modelValue"
                class="save-execute-overlay"
            >
                <div class="save-execute-backdrop" />
                <canvas ref="canvasEl" class="save-execute-canvas" />

                <div ref="wrapEl" class="save-execute-wrap">
                    <svg
                        class="save-execute-logo"
                        width="120"
                        height="116"
                        viewBox="0 0 66 64"
                        xmlns="http://www.w3.org/2000/svg"
                        aria-hidden="true"
                    >
                        <defs>
                            <linearGradient
                                id="saveExecuteSheen"
                                ref="sheenEl"
                                x1="-20"
                                y1="-20"
                                x2="0"
                                y2="0"
                                gradientUnits="userSpaceOnUse"
                            >
                                <stop offset="0%" stop-color="white" stop-opacity="0" />
                                <stop offset="40%" stop-color="white" stop-opacity="0" />
                                <stop offset="50%" stop-color="white" stop-opacity="0.45" />
                                <stop offset="60%" stop-color="white" stop-opacity="0" />
                                <stop offset="100%" stop-color="white" stop-opacity="0" />
                            </linearGradient>
                            <clipPath id="saveExecuteCircleClip">
                                <circle cx="32.5774" cy="56.8499" r="6.96655" />
                            </clipPath>
                            <clipPath id="saveExecuteRectClip1">
                                <rect x="46.1597" y="32.6304" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 46.1597 32.6304)" />
                            </clipPath>
                            <clipPath id="saveExecuteRectClip2">
                                <rect x="23.0796" y="32.6304" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 23.0796 32.6304)" />
                            </clipPath>
                            <clipPath id="saveExecuteRectClip3">
                                <rect x="0" y="32.6304" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 0 32.6304)" />
                            </clipPath>
                            <clipPath id="saveExecuteRectClip4">
                                <rect x="11.5667" y="21.064" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 11.5667 21.064)" />
                            </clipPath>
                            <clipPath id="saveExecuteRectClip5">
                                <rect x="23.0796" y="9.49756" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 23.0796 9.49756)" />
                            </clipPath>
                            <clipPath id="saveExecuteRectClip6">
                                <rect x="34.4961" y="21.064" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 34.4961 21.064)" />
                            </clipPath>
                        </defs>

                        <circle cx="32.5774" cy="56.8499" r="6.96655" fill="#F62E76" />
                        <rect x="46.1597" y="32.6304" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 46.1597 32.6304)" fill="#A950FF" />
                        <rect x="23.0796" y="32.6304" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 23.0796 32.6304)" fill="#A950FF" />
                        <rect x="0" y="32.6304" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 0 32.6304)" fill="#A950FF" />
                        <rect x="11.5667" y="21.064" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 11.5667 21.064)" fill="#CD88FF" />
                        <rect x="23.0796" y="9.49756" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 23.0796 9.49756)" fill="#E9C1FF" />
                        <rect x="34.4961" y="21.064" width="13.4319" height="13.4319" rx="3" transform="rotate(-45 34.4961 21.064)" fill="#CD88FF" />

                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteCircleClip)" fill="url(#saveExecuteSheen)" />
                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteRectClip1)" fill="url(#saveExecuteSheen)" />
                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteRectClip2)" fill="url(#saveExecuteSheen)" />
                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteRectClip3)" fill="url(#saveExecuteSheen)" />
                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteRectClip4)" fill="url(#saveExecuteSheen)" />
                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteRectClip5)" fill="url(#saveExecuteSheen)" />
                        <rect x="-10" y="-10" width="86" height="84" clip-path="url(#saveExecuteRectClip6)" fill="url(#saveExecuteSheen)" />
                    </svg>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>

<script setup lang="ts">
    import {nextTick, onBeforeUnmount, ref, watch} from "vue";

    const props = withDefaults(defineProps<{
        modelValue: boolean;
        text?: string;
    }>(), {
        text: "Flow Executed!",
    });

    const emit = defineEmits<{
        "update:modelValue": [boolean];
        finished: [];
    }>();

    const wrapEl = ref<HTMLElement | null>(null);
    const canvasEl = ref<HTMLCanvasElement | null>(null);
    const sheenEl = ref<SVGLinearGradientElement | null>(null);

    let running = false;
    let animationFrame: number | null = null;
    let completionTimeout: number | null = null;

    type Particle = {
        x: number;
        y: number;
        vx: number;
        vy: number;
        gravity: number;
        w: number;
        h: number;
        color: string;
        rot: number;
        rotV: number;
        wobble: number;
        wobbleSpeed: number;
        circ: boolean;
    };

    let particles: Particle[] = [];

    const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);
    const easeInOutSine = (t: number) => -(Math.cos(Math.PI * t) - 1) / 2;
    const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
    const PALETTE = ["#A950FF", "#F62E76", "#CD88FF", "#E9C1FF", "#ffffff", "#c084fc"];

    function clearAnimationFrame() {
        if (animationFrame !== null) {
            cancelAnimationFrame(animationFrame);
            animationFrame = null;
        }
    }

    function clearCompletionTimeout() {
        if (completionTimeout !== null) {
            window.clearTimeout(completionTimeout);
            completionTimeout = null;
        }
    }

    function resizeCanvas() {
        const canvas = canvasEl.value;
        if (!canvas) return;
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    }

    function resetElements() {
        clearAnimationFrame();
        clearCompletionTimeout();
        particles = [];
        running = false;

        if (wrapEl.value) {
            wrapEl.value.classList.remove("visible");
            wrapEl.value.style.transition = "none";
            wrapEl.value.style.opacity = "0";
            wrapEl.value.style.transform = "translateY(60px) scale(0.4)";
        }

        if (sheenEl.value) {
            sheenEl.value.setAttribute("x1", "-20");
            sheenEl.value.setAttribute("y1", "-20");
            sheenEl.value.setAttribute("x2", "0");
            sheenEl.value.setAttribute("y2", "0");
        }

        const ctx = canvasEl.value?.getContext("2d");
        if (ctx && canvasEl.value) {
            ctx.clearRect(0, 0, canvasEl.value.width, canvasEl.value.height);
        }
    }

    function tween(duration: number, step: (progress: number) => void, done?: () => void) {
        const start = performance.now();

        const frame = (now: number) => {
            const progress = Math.min((now - start) / duration, 1);
            step(progress);
            if (progress < 1) {
                animationFrame = requestAnimationFrame(frame);
            } else {
                animationFrame = null;
                done?.();
            }
        };

        animationFrame = requestAnimationFrame(frame);
    }

    function phaseRise(done?: () => void) {
        const wrap = wrapEl.value;
        if (!wrap) {
            done?.();
            return;
        }

        requestAnimationFrame(() => {
            wrap.style.transition = "";
            wrap.classList.add("visible");
            wrap.style.opacity = "1";
            wrap.style.transform = "translateY(0) scale(1)";
            const handler = (event: TransitionEvent) => {
                if (event.propertyName !== "transform") return;
                wrap.removeEventListener("transitionend", handler);
                done?.();
            };
            wrap.addEventListener("transitionend", handler);
        });
    }

    function phaseSweep(done?: () => void) {
        const sheen = sheenEl.value;
        if (!sheen) {
            done?.();
            return;
        }

        tween(750, progress => {
            const eased = easeInOutSine(progress);
            const pos = lerp(-20, 86, eased);
            sheen.setAttribute("x1", String(pos - 20));
            sheen.setAttribute("y1", String(pos - 20));
            sheen.setAttribute("x2", String(pos + 20));
            sheen.setAttribute("y2", String(pos + 20));
        }, done);
    }

    function spawnConfetti() {
        const canvas = canvasEl.value;
        if (!canvas) return;

        particles = [];
        for (let i = 0; i < 80; i++) {
            const fromLeft = i < 40;
            const w = 4 + Math.random() * 6;
            const h = 3 + Math.random() * 4;
            const x = fromLeft ? -10 - Math.random() * 40 : canvas.width + 10 + Math.random() * 40;
            const y = canvas.height * (0.1 + Math.random() * 0.5);
            const speed = 8 + Math.random() * 6;

            particles.push({
                x,
                y,
                vx: fromLeft ? speed : -speed,
                vy: -(3 + Math.random() * 5),
                gravity: 0.35 + Math.random() * 0.2,
                w,
                h,
                color: PALETTE[Math.floor(Math.random() * PALETTE.length)] ?? "#ffffff",
                rot: Math.random() * 360,
                rotV: (Math.random() - 0.5) * 6,
                wobble: Math.random() * Math.PI * 2,
                wobbleSpeed: 0.03 + Math.random() * 0.03,
                circ: Math.random() < 0.3,
            });
        }
    }

    function confettiLoop() {
        const canvas = canvasEl.value;
        const ctx = canvas?.getContext("2d");
        if (!canvas || !ctx) return;

        ctx.clearRect(0, 0, canvas.width, canvas.height);
        particles = particles.filter(particle => particle.y < canvas.height + 20);

        for (const particle of particles) {
            particle.wobble += particle.wobbleSpeed;
            particle.x += particle.vx + Math.sin(particle.wobble) * 0.8;
            particle.y += particle.vy;
            particle.vy += particle.gravity;
            particle.vx *= 0.97;
            particle.rot += particle.rotV;

            ctx.save();
            ctx.globalAlpha = 0.85;
            ctx.fillStyle = particle.color;
            ctx.translate(particle.x, particle.y);
            ctx.rotate((particle.rot * Math.PI) / 180);
            if (particle.circ) {
                ctx.beginPath();
                ctx.arc(0, 0, particle.w / 2, 0, Math.PI * 2);
                ctx.fill();
            } else {
                ctx.fillRect(-particle.w / 2, -particle.h / 2, particle.w, particle.h);
            }
            ctx.restore();
        }

        if (particles.length > 0) {
            animationFrame = requestAnimationFrame(confettiLoop);
        } else {
            animationFrame = null;
        }
    }

    function launchConfetti() {
        const wrap = wrapEl.value;
        if (!wrap) return;

        tween(400, progress => {
            wrap.style.opacity = String(1 - easeOutCubic(progress));
        });
        spawnConfetti();
        confettiLoop();
    }

    async function runAnimation() {
        if (running) return;

        await nextTick();
        resizeCanvas();
        resetElements();
        running = true;

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                phaseRise(() => phaseSweep(() => {
                    launchConfetti();
                    completionTimeout = window.setTimeout(() => {
                        emit("update:modelValue", false);
                        emit("finished");
                    }, 1800);
                }));
            });
        });
    }

    watch(
        () => props.modelValue,
        value => {
            if (value) {
                void runAnimation();
            } else {
                resetElements();
            }
        },
        {immediate: true},
    );

    onBeforeUnmount(() => {
        resetElements();
    });
</script>

<style scoped lang="scss">
    .save-execute-overlay {
        position: fixed;
        inset: 0;
        z-index: 4000;
        display: flex;
        align-items: center;
        justify-content: center;
        pointer-events: none;
        overflow: hidden;
    }

    .save-execute-backdrop {
        position: absolute;
        inset: 0;
        background: rgba(10, 10, 15, 0.72);
    }

    .save-execute-canvas {
        position: fixed;
        inset: 0;
        z-index: 4001;
        pointer-events: none;
    }

    .save-execute-wrap {
        position: relative;
        z-index: 4002;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 16px;
        opacity: 0;
        transform: translateY(60px) scale(0.4);
        transition:
            opacity 0.15s ease,
            transform 0.55s linear(
                0, 0.0221, 0.0739, 0.1485, 0.2394, 0.3402, 0.4458, 0.5517, 0.6541,
                0.7502, 0.8375, 0.9149, 0.9813, 1.0364, 1.0803, 1.1136, 1.1369,
                1.1513, 1.1579, 1.1578, 1.1522, 1.1424, 1.1294, 1.1142, 1.0978,
                1.081, 1.0643, 1.0485, 1.0338, 1.0205, 1.009, 0.9992, 0.9912,
                0.9849, 0.9803, 0.9772, 0.9754, 0.9748, 0.9752, 0.9764, 0.9782,
                0.9804, 0.9829, 0.9856, 0.9882, 0.9908, 0.9933, 0.9955, 0.9975,
                0.9992, 1.0007, 1.0018, 1.0027, 1.0034, 1.0038, 1.004, 1.004,
                1.0039, 1.0036, 1.0033, 1.0029, 1.0025, 1.0021, 1.0017, 1.0013,
                1.0009, 1.0006, 1.0003, 1, 0.9998, 0.9996, 0.9995, 0.9994,
                0.9994, 0.9994, 0.9994, 0.9994, 0.9994, 0.9995, 0.9996, 0.9996,
                0.9997, 0.9998, 0.9998, 0.9999, 0.9999, 1, 1, 1, 1.0001,
                1.0001, 1.0001, 1.0001, 1.0001, 1.0001, 1.0001, 1.0001, 1.0001,
                1.0001, 1.0001, 1
            );
    }

    .save-execute-wrap.visible {
        opacity: 1;
        transform: translateY(0) scale(1);
    }

    .save-execute-logo {
        display: block;
        filter: drop-shadow(0 18px 36px rgba(169, 80, 255, 0.18));
    }

    .save-execute-fade-enter-active,
    .save-execute-fade-leave-active {
        transition: opacity 0.2s ease;
    }

    .save-execute-fade-enter-from,
    .save-execute-fade-leave-to {
        opacity: 0;
    }
</style>
