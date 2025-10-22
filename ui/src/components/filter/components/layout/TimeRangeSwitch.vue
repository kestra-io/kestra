<template>
    <div class="switch-container">
        <div class="switch-wrapper">
            <input
                id="time-range-switch"
                type="checkbox"
                :checked="modelValue === 'custom'"
                @change="handleChange"
            >
            <label for="time-range-switch" class="switch-label">
                <span class="switch-option left">{{ $t("filter.timerange.predefined") }}</span>
                <span class="switch-option right">{{ $t("filter.timerange.custom") }}</span>
                <div class="switch-slider" />
            </label>
        </div>
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        modelValue: "predefined" | "custom"
    }>();

    const emit = defineEmits<{
        "update:modelValue": [value: "predefined" | "custom"]
    }>();

    const handleChange = (event: Event) => {
        const target = event.target as HTMLInputElement;
        emit("update:modelValue", target.checked ? "custom" : "predefined");
    };
</script>

<style lang="scss" scoped>
.switch-container {
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 0.5rem;
    padding-left: 1rem;
    padding-right: 1rem;
}

.switch-wrapper {
    display: inline-block;
    position: relative;
    width: 100%;

    input[type="checkbox"] {
        opacity: 0;
        position: absolute;
        z-index: -1;

        &:checked ~ .switch-label .switch-slider {
            transform: translateX(100%);
        }
    }
}

.switch-label {
    align-items: center;
    background-color: var(--ks-background-body);
    border: 1px solid var(--ks-border-primary);
    border-radius: 20px;
    cursor: pointer;
    display: flex;
    padding: 4px;
    position: relative;
    transition: all 0.3s ease;
    user-select: none;
    justify-content: space-around;
    box-shadow: rgba(17, 17, 26, 0.1) 0px 0px 16px;
}

.switch-option {
    color: var(--ks-content-primary);
    font-size: 12px;
    font-weight: 500;
    padding: 6px 16px;
    position: relative;
    transition: color 0.3s ease;
    white-space: nowrap;
    z-index: 2;
}

.switch-slider {
    background-color: var(--ks-background-card);
    border-radius: 16px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
    height: calc(100% - 8px);
    left: 4px;
    position: absolute;
    top: 4px;
    transition: transform 0.3s ease;
    width: calc(50% - 4px);
    z-index: 1;
}
</style>