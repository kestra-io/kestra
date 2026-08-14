import path from "path"
import {fileURLToPath} from "url"
import {compareTranslations, DEFAULT_LANGUAGES} from "./compareTranslations"

const here = path.dirname(fileURLToPath(import.meta.url))

// The tooling lives here; the language files stay in `src/` because the app imports them
// (`src/translations/i18n.ts` globs them relative to itself).
compareTranslations(
    path.resolve(here, "../../src/translations"),
    DEFAULT_LANGUAGES,
    path.join(here, "fingerprints.json"),
)
