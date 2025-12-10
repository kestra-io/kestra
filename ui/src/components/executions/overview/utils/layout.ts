import {useBreakpoints, breakpointsElement} from "@vueuse/core";

export const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("md");
