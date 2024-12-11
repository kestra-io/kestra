/// <reference types="vite/client" />

interface ImportMetaEnv {
    VITE_APP_API_URL: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}

interface Window {
    KESTRA_BASE_PATH: string
    KESTRA_GOOGLE_ANALYTICS: string
    KESTRA_UI_PATH: string
}