import {kestraDark} from "./kestra-dark.ts"
import {kestraDarkClassic} from "./kestra-dark-classic.ts"
import {kestraDarkHighContrast, kestraLightHighContrast} from "./kestra-high-contrast.ts"
import {kestraLight} from "./kestra-light.ts"
import {kestraMidnight} from "./kestra-midnight.ts"
import type {SyntaxColors} from "../types.ts"

export {kestraDark, kestraLight, kestraDarkClassic, kestraMidnight, kestraDarkHighContrast, kestraLightHighContrast}

export const themes = [kestraDark, kestraLight, kestraDarkClassic, kestraMidnight, kestraDarkHighContrast, kestraLightHighContrast]

export const syntaxColors: ReadonlyMap<string | undefined, SyntaxColors> = new Map(themes.map(theme => [theme.name, theme.syntax]))
