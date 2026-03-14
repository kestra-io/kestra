import {setProjectAnnotations} from "@storybook/vue3-vite"
import * as projectAnnotations from "./preview"

// Apply global Storybook decorators/parameters (registers ElementPlus, etc.)
setProjectAnnotations([projectAnnotations])
