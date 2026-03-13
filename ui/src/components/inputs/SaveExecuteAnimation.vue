<template>
    <Teleport to="body">
        <Transition name="save-execute-fade">
            <div
                v-if="modelValue"
                class="save-execute-overlay"
            >
                <div class="save-execute-backdrop" />

                <div ref="wrapEl" class="save-execute-wrap">
                    <div ref="aura1El" class="save-execute-aura aura-1" />
                    <div ref="aura2El" class="save-execute-aura aura-2" />
                    <div ref="aura3El" class="save-execute-aura aura-3" />

                    <div ref="ring1El" class="save-execute-ring ring-1" />
                    <div ref="ring2El" class="save-execute-ring ring-2" />
                    <div ref="ring3El" class="save-execute-ring ring-3" />

                    <svg
                        class="save-execute-bolt"
                        xmlns="http://www.w3.org/2000/svg"
                        width="110"
                        height="130"
                        viewBox="0 0 24 28"
                        aria-hidden="true"
                    >
                        <defs>
                            <linearGradient id="saveExecuteBoltGradient" x1="0%" y1="0%" x2="50%" y2="100%">
                                <stop offset="0%" stop-color="#fff" />
                                <stop offset="40%" stop-color="#c4b5fd" />
                                <stop offset="100%" stop-color="#7c3aed" />
                            </linearGradient>
                            <filter id="saveExecuteGlow">
                                <feGaussianBlur stdDeviation="2" result="blur" />
                                <feMerge>
                                    <feMergeNode in="blur" />
                                    <feMergeNode in="SourceGraphic" />
                                </feMerge>
                            </filter>
                        </defs>
                        <polygon
                            points="13,2 4,15 11,15 11,26 20,13 13,13"
                            fill="url(#saveExecuteBoltGradient)"
                            filter="url(#saveExecuteGlow)"
                            stroke="rgba(255,255,255,0.6)"
                            stroke-width="0.5"
                        />
                    </svg>

                    <div
                        v-for="sparkIndex in 8"
                        :key="sparkIndex"
                        :ref="el => setSparkRef(el, sparkIndex - 1)"
                        class="save-execute-spark"
                    />

                    <div ref="successTextEl" class="save-execute-text">
                        {{ text }}
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>

<script setup lang="ts">
    import {nextTick, onBeforeUnmount, ref, watch, type ComponentPublicInstance} from "vue";

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
    const aura1El = ref<HTMLElement | null>(null);
    const aura2El = ref<HTMLElement | null>(null);
    const aura3El = ref<HTMLElement | null>(null);
    const ring1El = ref<HTMLElement | null>(null);
    const ring2El = ref<HTMLElement | null>(null);
    const ring3El = ref<HTMLElement | null>(null);
    const successTextEl = ref<HTMLElement | null>(null);
    const sparkEls = ref<(HTMLElement | null)[]>(Array.from({length: 8}, () => null));
    const starParticles = ref<HTMLElement[]>([]);

    let timeouts: number[] = [];
    let running = false;

    function setSparkRef(el: Element | ComponentPublicInstance | null, index: number) {
        if (el && "$el" in el) {
            sparkEls.value[index] = el.$el as HTMLElement | null;
            return;
        }
        sparkEls.value[index] = el as HTMLElement | null;
    }

    function queueTimeout(callback: () => void, delay: number) {
        const timeout = window.setTimeout(callback, delay);
        timeouts.push(timeout);
    }

    function cleanupTimers() {
        timeouts.forEach(timeout => window.clearTimeout(timeout));
        timeouts = [];
    }

    function resetElements() {
        const wrap = wrapEl.value;
        const successText = successTextEl.value;
        const auras = [aura1El.value, aura2El.value, aura3El.value];
        const rings = [ring1El.value, ring2El.value, ring3El.value];

        if (wrap) {
            wrap.style.transition = "none";
            wrap.style.opacity = "0";
            wrap.style.transform = "scale(0.2)";
        }

        if (successText) {
            successText.style.transition = "none";
            successText.style.opacity = "0";
            successText.style.transform = "translateX(-50%)";
        }

        auras.forEach(aura => {
            if (!aura) return;
            aura.style.transition = "none";
            aura.style.opacity = "0";
        });

        rings.forEach(ring => {
            if (!ring) return;
            ring.style.transition = "none";
            ring.style.opacity = "0";
            ring.style.transform = "scale(1)";
        });

        sparkEls.value.forEach(spark => {
            if (!spark) return;
            spark.style.transition = "none";
            spark.style.opacity = "0";
            spark.style.transform = "translate(0,0) scale(1)";
        });

        starParticles.value.forEach(particle => particle.remove());
        starParticles.value = [];
        running = false;
    }

    function makeStarCanvas(size: number, color: string, glow: boolean) {
        const canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
            return "";
        }

        const cx = size / 2;
        const cy = size / 2;
        const outer = size / 2 - 1;
        const inner = outer * 0.38;

        ctx.save();
        if (glow) {
            ctx.shadowColor = color;
            ctx.shadowBlur = size * 0.35;
        }

        ctx.beginPath();
        for (let i = 0; i < 8; i++) {
            const radius = i % 2 === 0 ? outer : inner;
            const angle = (i / 8) * Math.PI * 2 - Math.PI / 2;
            const px = cx + Math.cos(angle) * radius;
            const py = cy + Math.sin(angle) * radius;
            if (i === 0) {
                ctx.moveTo(px, py);
            } else {
                ctx.lineTo(px, py);
            }
        }
        ctx.closePath();
        ctx.fillStyle = color;
        ctx.fill();
        ctx.restore();

        return canvas.toDataURL();
    }

    const starImages = [
        makeStarCanvas(18, "rgba(255,255,255,0.95)", true),
        makeStarCanvas(13, "rgba(196,181,253,0.95)", true),
        makeStarCanvas(10, "rgba(167,139,250,0.9)", true),
        makeStarCanvas(8, "rgba(255,255,255,0.7)", false),
        makeStarCanvas(22, "rgba(221,214,254,0.85)", true),
    ];

    function launchStars() {
        const count = 38;
        const cx = window.innerWidth / 2;
        const cy = window.innerHeight / 2;

        for (let i = 0; i < count; i++) {
            const particle = document.createElement("img");
            particle.className = "save-execute-star-particle";
            particle.src = starImages[Math.floor(Math.random() * starImages.length)] ?? "";

            const size = 8 + Math.random() * 18;
            particle.style.width = `${size}px`;
            particle.style.height = `${size}px`;
            particle.style.left = `${cx}px`;
            particle.style.top = `${cy}px`;
            particle.style.transformOrigin = "center";
            document.body.appendChild(particle);
            starParticles.value.push(particle);

            const angle = Math.random() * Math.PI * 2;
            const speed = 120 + Math.random() * 260;
            const tx = Math.cos(angle) * speed;
            const ty = Math.sin(angle) * speed - Math.random() * 60;
            const rotation = (Math.random() - 0.5) * 360;
            const duration = 900 + Math.random() * 600;
            const delay = Math.random() * 100;
            const initialRotation = Math.random() * 360;

            particle.style.opacity = "1";
            particle.style.transform = `translate(-50%,-50%) rotate(${initialRotation}deg) scale(1)`;

            requestAnimationFrame(() => {
                particle.style.transition = `transform ${duration}ms cubic-bezier(.2,.8,.4,1) ${delay}ms, opacity ${duration * 0.55}ms ease ${delay + duration * 0.45}ms`;
                particle.style.transform = `translate(calc(-50% + ${tx}px), calc(-50% + ${ty}px)) rotate(${initialRotation + rotation}deg) scale(0.3)`;
                particle.style.opacity = "0";
            });

            queueTimeout(() => {
                particle.remove();
                starParticles.value = starParticles.value.filter(item => item !== particle);
            }, duration + delay + 50);
        }
    }

    function shootSparks() {
        sparkEls.value.forEach((spark, index) => {
            if (!spark) return;
            const angle = (index / 8) * Math.PI * 2;
            const distance = 90 + Math.random() * 50;
            spark.style.transition = "none";
            spark.style.opacity = "1";
            spark.style.transform = "translate(0,0) scale(1)";
            spark.style.background = "#c4b5fd";
            spark.style.boxShadow = "0 0 6px #a78bfa";

            requestAnimationFrame(() => {
                spark.style.transition = "transform .8s ease-out, opacity .8s ease-out";
                spark.style.transform = `translate(${Math.cos(angle) * distance}px, ${Math.sin(angle) * distance}px) scale(0)`;
                spark.style.opacity = "0";
            });
        });
    }

    async function runAnimation() {
        if (running) {
            return;
        }

        await nextTick();

        const wrap = wrapEl.value;
        const successText = successTextEl.value;
        if (!wrap || !successText) {
            return;
        }

        running = true;

        wrap.style.opacity = "1";
        wrap.style.transition = "opacity .15s, transform .45s cubic-bezier(.17,.67,.3,1.4)";
        wrap.style.transform = "scale(1)";

        queueTimeout(() => {
            [aura1El.value, aura2El.value, aura3El.value].forEach((aura, index) => {
                if (!aura) return;
                aura.style.transition = `opacity .4s ${index * 80}ms`;
                aura.style.opacity = "1";
            });
        }, 80);

        [ring1El.value, ring2El.value, ring3El.value].forEach((ring, index) => {
            queueTimeout(() => {
                if (!ring) return;
                ring.style.transition = "none";
                ring.style.opacity = "0.9";
                ring.style.transform = "scale(1)";
                requestAnimationFrame(() => {
                    ring.style.transition = "transform 1.2s ease-out, opacity 1.2s ease-out";
                    ring.style.transform = "scale(2.8)";
                    ring.style.opacity = "0";
                });
            }, 200 + index * 200);
        });

        queueTimeout(() => {
            shootSparks();
        }, 250);

        queueTimeout(() => {
            launchStars();
        }, 280);

        queueTimeout(() => {
            successText.style.transition = "opacity .35s, transform .4s cubic-bezier(.17,.67,.3,1.3)";
            successText.style.opacity = "1";
            successText.style.transform = "translateX(-50%) translateY(-10px)";
        }, 500);

        queueTimeout(() => {
            [ring1El.value, ring2El.value, ring3El.value].forEach(ring => {
                if (!ring) return;
                ring.style.transition = "none";
                ring.style.opacity = "0.7";
                ring.style.transform = "scale(1)";
                requestAnimationFrame(() => {
                    ring.style.transition = "transform 1s ease-out, opacity 1s ease-out";
                    ring.style.transform = "scale(3.5)";
                    ring.style.opacity = "0";
                });
            });
        }, 700);

        queueTimeout(() => {
            wrap.style.transition = "opacity .5s, transform .5s ease-in";
            wrap.style.opacity = "0";
            wrap.style.transform = "scale(1.3)";
            successText.style.opacity = "0";
        }, 2000);

        queueTimeout(() => {
            emit("update:modelValue", false);
            emit("finished");
        }, 2600);
    }

    watch(
        () => props.modelValue,
        value => {
            cleanupTimers();
            if (value) {
                void runAnimation();
            } else {
                resetElements();
            }
        },
        {immediate: true},
    );

    onBeforeUnmount(() => {
        cleanupTimers();
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
    }

    .save-execute-backdrop {
        position: absolute;
        inset: 0;
        background: rgba(10, 5, 30, 0.65);
    }

    .save-execute-wrap {
        position: absolute;
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transform: scale(0.2);
    }

    .save-execute-ring,
    .save-execute-aura {
        position: absolute;
        border-radius: 999px;
        opacity: 0;
    }

    .save-execute-ring {
        border: 3px solid rgba(124, 58, 237, 0.6);
    }

    .save-execute-aura.aura-1 {
        width: 240px;
        height: 240px;
        background: radial-gradient(circle, rgba(167, 139, 250, 0.55) 0%, transparent 70%);
    }

    .save-execute-aura.aura-2 {
        width: 380px;
        height: 380px;
        background: radial-gradient(circle, rgba(124, 58, 237, 0.35) 0%, transparent 65%);
    }

    .save-execute-aura.aura-3 {
        width: 520px;
        height: 520px;
        background: radial-gradient(circle, rgba(91, 33, 182, 0.2) 0%, transparent 60%);
    }

    .save-execute-ring.ring-1 {
        width: 150px;
        height: 150px;
    }

    .save-execute-ring.ring-2 {
        width: 220px;
        height: 220px;
        border-color: rgba(167, 139, 250, 0.5);
    }

    .save-execute-ring.ring-3 {
        width: 310px;
        height: 310px;
        border-color: rgba(124, 58, 237, 0.35);
    }

    .save-execute-bolt {
        position: relative;
        z-index: 5;
        filter: drop-shadow(0 0 18px #a78bfa) drop-shadow(0 0 40px #7c3aed);
    }

    .save-execute-spark {
        position: absolute;
        width: 4px;
        height: 4px;
        border-radius: 50%;
        opacity: 0;
    }

    .save-execute-text {
        position: absolute;
        bottom: -80px;
        left: 50%;
        transform: translateX(-50%);
        color: #fff;
        font-size: 22px;
        font-weight: 800;
        letter-spacing: 1px;
        white-space: nowrap;
        opacity: 0;
        text-shadow: 0 0 20px #a78bfa, 0 2px 8px rgba(0, 0, 0, 0.4);
    }

    :global(.save-execute-star-particle) {
        position: fixed;
        z-index: 4001;
        opacity: 0;
        pointer-events: none;
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
