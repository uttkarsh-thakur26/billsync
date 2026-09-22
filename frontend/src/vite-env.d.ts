/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin. Defaults to http://localhost:8080 when unset. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
