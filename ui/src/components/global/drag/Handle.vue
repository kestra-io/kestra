<template>
    <div class="handle" @mousedown="startDragging" />
</template>

<script setup lang="ts">
    const emits = defineEmits(["update:left"]);

    const props = defineProps({left: {type: Number, default: 70}});

    const startDragging = (event: MouseEvent) => {
        const startX = event.clientX;
        const startWidth = props.left;

        const onMouseMove = (moveEvent: MouseEvent) => {
            const delta = ((moveEvent.clientX - startX) / window.innerWidth) * 100;
            let width = Math.max(30, Math.min(70, startWidth + delta));

            emits("update:left", width);
        };

        const onMouseUp = () => {
            document.removeEventListener("mousemove", onMouseMove);
            document.removeEventListener("mouseup", onMouseUp);
        };

        document.addEventListener("mousemove", onMouseMove);
        document.addEventListener("mouseup", onMouseUp);
    };
</script>

<style scoped lang="scss">
.handle {
    user-select: none;
    position: relative;
    width: 3px;
    background-color: var(--ks-border-primary);

    &::before {
        content: "";
        position: absolute;
        top: calc(50% - 20px);
        left: 50%;
        transform: translateX(-50%);
        width: 10px;
        height: 40px;
        cursor: ew-resize;
        background: repeating-linear-gradient(
            to bottom,
            var(--ks-border-active),
            var(--ks-border-active) 2px,
            transparent 2px,
            transparent 4px
        );
    }
}
</style>
