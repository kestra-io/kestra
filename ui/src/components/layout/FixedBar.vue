<template>
    <nav class="fixed-bar" :style="{top: `${topOffset}px`}">
        <slot />
    </nav>
</template>
<script>
    // 5 rem
    const topBarInitialOffset = 80;
    export default {
        created() {
            // 5 rem = 80px = top offset of the fixed bar to ensure smooth bar transition
            window.onscroll = () => {
                this.topOffset = Math.max(0, topBarInitialOffset - window.scrollY)
                if (window.scrollY > topBarInitialOffset) {
                    this.$el.classList.add("with-bar");
                } else {
                    this.$el.classList.remove("with-bar");
                }
            }
        },
        data() {
            return {
                topOffset: topBarInitialOffset
            }
        }
    }
</script>
<style lang="scss">
    .fixed-bar {
        position: fixed;
        right: 0;
        left: 0;
        border-radius: 0;
        z-index: 90;
        padding-top: var(--spacer);
        text-align: right;
        transition: margin-left ease 0.2s;
        padding-right: calc(var(--spacer) * 4);

        &.with-bar {
            border-bottom: 1px solid var(--bs-border-color);
            background-color: var(--bs-white);

            html.dark & {
                background-color: var(--bs-gray-100-darken-5);
            }
        }

        button {
            margin-left: var(--spacer);

            span:first-child {
                margin-right: calc(var(--spacer) / 3);
            }
        }

        ul {
            display: flex;
            list-style: none;
            margin: 0;
            flex-wrap: nowrap;
            padding: 0;
            justify-content: flex-end;

            li.spacer {
                flex-grow: 2;
            }

            li.left {
                margin-left: 0;
            }

            li {
                p {
                    padding: 8px 15px;
                    font-size: var(--font-size-sm);
                    line-height: var(--font-size-sm);
                    margin-bottom: 0;
                }
            }
        }

        .menu-collapsed & {
            margin-left: var(--menu-collapsed-width);
        }
        .menu-not-collapsed & {
            margin-left: var(--menu-width);
        }
    }
</style>
