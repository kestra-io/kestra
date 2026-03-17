<template>
    <Teleport to="body">
        <Transition name="save-execute-fade">
            <div
                v-if="modelValue"
                class="save-execute-overlay"
            >
                <div class="save-execute-backdrop" />
                <canvas ref="canvasEl" class="save-execute-canvas" />
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

    const canvasEl = ref<HTMLCanvasElement | null>(null);

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

        const ctx = canvasEl.value?.getContext("2d");
        if (ctx && canvasEl.value) {
            ctx.clearRect(0, 0, canvasEl.value.width, canvasEl.value.height);
        }
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
        spawnConfetti();
        confettiLoop();
    }

    async function runAnimation() {
        if (running) return;

        await nextTick();
        resizeCanvas();
        resetElements();
        running = true;

        launchConfetti();
        completionTimeout = window.setTimeout(() => {
            emit("update:modelValue", false);
            emit("finished");
        }, 1800);
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
        z-index: 4003;
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
