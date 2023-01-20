<template>
    <nav class="bottom-line">
        <slot />
    </nav>
</template>
<script>
    export default {}
</script>
<style lang="scss">
    .bottom-line {
        position: fixed;
        bottom: 0;
        right: 0;
        left: 0px;
        border-radius: 0;
        border-top: 1px solid var(--bs-border-color);
        z-index: 90;
        background-color: var(--bs-white);
        padding: 0.5rem 1rem;
        text-align: right;
        transition: margin-left ease 0.2s;

        html.dark & {
            background-color: var(--bs-gray-100-darken-3);
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
