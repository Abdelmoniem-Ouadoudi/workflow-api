/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The gateway's public URL. Unset in development, where it falls back to localhost:8090. */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
