<template>
    <el-card class="edition-card">
        <template #header>
            <div class="header-content">
                <el-text class="title">
                    {{ name }}
                </el-text>
                <el-text class="price" v-if="price">
                    {{ price }}
                </el-text>
            </div>
        </template>
        <div class="features-container">
            <div
                v-for="feature in features"
                :key="feature"
                class="feature-row"
            >
                <div class="check-column">
                    <CheckBold class="check-icon" />
                </div>
                <div class="feature-column">
                    <el-text>
                        {{ feature }}
                    </el-text>
                </div>
            </div>
        </div>

        <a v-if="button?.href" class="button-link" :href="button.href">
            <el-button type="primary" class="action-button">
                {{ button.text }}
            </el-button>
        </a>
        <el-button v-else-if="button" class="action-button disabled" disabled>
            {{ button.text }}
        </el-button>
    </el-card>
</template>

<script setup lang="ts">
    import CheckBold from "vue-material-design-icons/CheckBold.vue"

    interface ButtonConfig {
        text: string
        href?: string
    }

    interface Props {
        name: string
        price?: string
        features?: string[]
        button?: ButtonConfig
    }

    defineProps<Props>()
</script>

<style scoped lang="scss">
.edition-card {
    padding: 2rem;
    background-color: var(--ks-background-body);
    display: flex;
    flex-direction: column;
    gap: 1rem;
    box-shadow: 0 2px 4px var(--ks-card-shadow);


    :deep(.el-card__header) {
        border-bottom: 0;
        padding: 0;

        .header-content {
            .title {
                font-size: 1.25rem;
                display: block;
            }

            .price {
                display: block;
                font-weight: normal;
            }
        }
    }

    :deep(.el-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding: 0;
    }

    .features-container {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;

        .feature-row {
            display: flex;
            align-items: flex-start;
            gap: 0.75rem;

            .check-column {
                flex-shrink: 0;
                width: 20px;
                display: flex;
                justify-content: center;
                align-items: flex-start;
                padding-top: 2px;

                .check-icon {
                    color: var(--ks-content-success);
                }
            }

            .feature-column {
                flex: 1;
                min-width: 0;

                .el-text {
                    line-height: 1.4;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    hyphens: auto;
                }
            }
        }
    }

    .button-link {
        margin-top: auto;
        text-decoration: none;

        .action-button {
            width: 100%;
        }
    }

    .action-button {
        margin-top: auto;
        width: 100%;
        white-space: normal;
        height: auto;
        box-sizing: border-box;
    }
}
</style>