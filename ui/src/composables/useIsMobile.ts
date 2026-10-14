import {breakpointsElement, useBreakpoints} from "@vueuse/core"

export function useIsMobile() {
    return useBreakpoints(breakpointsElement).smaller("sm")
}
