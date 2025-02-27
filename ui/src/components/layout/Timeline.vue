<template>
    <div class="mb-3 timeline">
        <div v-if="isToggle" class="timeline-header">
            <el-button class="see_states" @click="toggleStates">
                {{ $t('see_all_states') }}
                <el-icon class="el-icon--right">
                    <ChevronUp v-if="showStates" />
                    <ChevronDown v-else />
                </el-icon>
            </el-button>
        </div>
        
        <div 
            v-for="(history, index) in displayedHistories"
            :key="'timeline-' + index" 
            class="timeline-item"
        >
            <div class="timeline-content">
                <span class="timeline-date">{{ $filters.date(history.date, 'iso') }}</span>
                <div 
                    class="timeline-dot" 
                    :style="getStyle(history.state)" 
                />
                <span class="timeline-state">{{ history.state }}</span>
            </div>
        </div>
    </div>
</template>

<script setup>
    import {ref, computed, onMounted, onUnmounted} from "vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    const props = defineProps({
        histories: {
            type: Array,
            default: () => []
        }
    })

    const showStates = ref(false)
    const windowWidth = ref(window.innerWidth)

    const handleResize = () => {
        windowWidth.value = window.innerWidth
    }

    onMounted(() => {
        window.addEventListener("resize", handleResize)
    })

    onUnmounted(() => {
        window.removeEventListener("resize", handleResize)
    })

    const isToggle = computed(() => {
        const minWidth = 480
        const maxWidth = 960
        return windowWidth.value >= minWidth && 
            windowWidth.value <= maxWidth && 
            props.histories.length > 1
    })

    const displayedHistories = computed(() => {
        if (!isToggle.value || showStates.value) {
            return props.histories
        }
        return [props.histories[props.histories.length - 1]]
    })

    const toggleStates = () => {
        showStates.value = !showStates.value
    }

    const getStyle = (state) => ({
        backgroundColor: `var(--ks-chart-${state.toLowerCase()})`
    })
</script>

<style lang="scss" scoped>
.timeline {
    $min: 480px;
    $max: 960px;
    
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px;
    position: relative;
    border-radius: 5px;
    background-color: var(--ks-background-body);
    box-shadow: 0 2px 4px 0 var(--ks-card-shadow);
    border: 1px solid var(--ks-border-primary);

    @media (min-width: $min) and (max-width: $max) {
        flex-direction: column;
        align-items: center;
        padding: 20px;
    }

    &-header {
        width: 100%;
        margin-bottom: 1rem;
        display: flex;
        justify-content: center;
        
        .see_states {
            background: none;
            border: none;
            cursor: pointer;
            color: var(--ks-content-link);
            font-size: 12px;
        }
    }

    &-item {
        position: relative;
        text-align: center;
        flex: 1;

        @media (min-width: $min) and (max-width: $max) {
            width: 100%;
            margin-bottom: 30px;
            text-align: center;

            &:last-child {
                margin-bottom: 0;
            }
        }

        &:not(:last-child)::after {
            content: '';
            position: absolute;
            background-color: var(--ks-border-primary);
            
            @media (min-width: $min) and (max-width: $max) {
                top: 100%;
                left: 50%;
                transform: translateX(-50%);
                width: 1px;
                height: 20px;
                margin-top: 6px;
            }

            @media not all and (min-width: $min) and (max-width: $max) {
                top: 50%;
                left: 50%;
                transform: translateY(-50%);
                width: calc(100% - 15px);
                height: 1px;
            }
        }
    }

    &-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
        font-size: 12px;
    }

    &-dot {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        width: 12px;
        height: 12px;
        border-radius: 50%;
        z-index: 10;

        @media (min-width: $min) and (max-width: $max) {
            position: relative;
            display: inline-block;
            top: auto;
            left: auto;
            transform: none;
        }
    }

    &-date {
        margin-bottom: 1rem;
        font-size: 12px;
        color: var(--ks-content-tertiary);

        @media (min-width: $min) and (max-width: $max) {
            margin-bottom: 0;
        }
    }

    &-state {
        margin-top: 0.5rem;
        color: var(--ks-content-secondary);

        @media (min-width: $min) and (max-width: $max) {
            margin-top: 0;
        }
    }
}
</style>
