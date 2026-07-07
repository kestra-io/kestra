import path from "path"
import {fileURLToPath} from "url"
import {compareTranslations} from "./compareTranslations"

// OSS language files sit alongside this script.
compareTranslations(path.dirname(fileURLToPath(import.meta.url)))
