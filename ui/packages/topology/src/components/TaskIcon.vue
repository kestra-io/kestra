<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <Tooltip v-if="!onlyIcon" :content="cls">
            <div class="ks-task-icon__icon" :style="styles" />
        </Tooltip>

        <div v-else class="ks-task-icon__icon" :style="styles" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsTooltip as Tooltip, cssVar} from "@kestra-io/design-system"

    defineOptions({name: "TaskIcon"})

    const props = defineProps<{
        customIcon?: {icon: string};
        cls?: string;
        theme?: "dark" | "light";
        icons?: Record<string, {icon: string; flowable: boolean}>;
        onlyIcon?: boolean;
        variable?: string;
    }>()

    const backgroundImage = computed(() => `data:image/svg+xml;base64,${imageBase64.value}`)

    const classes = computed(() => ({
        "ks-task-icon--flowable": icon.value && "flowable" in icon.value ? icon.value.flowable : false,
    }))

    const styles = computed(() => ({backgroundImage: `url(${backgroundImage.value})`}))

    const imageBase64 = computed(() => {
        let localIcon = icon.value?.icon ? window.atob(icon.value.icon) : undefined

        if (!localIcon) {
            localIcon = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
                "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
                "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
                "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
                "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
                "</svg>"
        }

        let color = cssVar("--ks-text-primary") || cssVar("--ks-content-inverse")

        if (props.theme) {
            color = (props.theme === "dark" ? cssVar("--ks-content-inverse") : cssVar("--ks-text-primary")) || color
        }

        if (props.variable) {
            color = cssVar(props.variable) || color
        }

        localIcon = localIcon.replace(/currentColor/g, color)
        return window.btoa(localIcon)
    })

    const icon = computed(() => {
        return props.cls ? (props.icons ?? {})[innerClassToParent(props.cls)] : props.customIcon
    })

    function innerClassToParent(cls: string) {
        return cls.includes("$") ? cls.substring(0, cls.indexOf("$")) : cls
    }
</script>

<style lang="scss" scoped>
    .ks-task-icon {
        display: inline-block;
        width: 100%;
        height: 100%;
        position: relative;

        :deep(span) {
            position: absolute;
            padding: 1px;
            left: 0;
            display: block;
            width: 100%;
            height: 100%;
        }

        &__icon {
            width: 100%;
            height: 100%;
            display: block;
            border-radius: 3px;
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center center;
        }
    }
</style>
