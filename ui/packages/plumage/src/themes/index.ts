import {kestraDark} from "./kestra-dark.ts"
import {kestraDarkClassic} from "./kestra-dark-classic.ts"
import {kestraDarkHighContrast, kestraLightHighContrast} from "./kestra-high-contrast.ts"
import {kestraLight} from "./kestra-light.ts"
import {kestraMidnight} from "./kestra-midnight.ts"

export {kestraDark, kestraLight, kestraDarkClassic, kestraMidnight, kestraDarkHighContrast, kestraLightHighContrast}

export const themes = [kestraDark, kestraLight, kestraDarkClassic, kestraMidnight, kestraDarkHighContrast, kestraLightHighContrast]

export const pebbleColors: ReadonlyMap<string | undefined, string> = new Map(themes.map(theme => [theme.name, theme.syntax.pebble]))
