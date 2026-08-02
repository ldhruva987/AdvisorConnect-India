/**
 * Environment fixes that must run BEFORE any application module is imported.
 *
 * This is listed as the first entry in `vitest.config.ts`'s `setupFiles` (ahead
 * of `setup.ts`) rather than being imported from it: vitest evaluates setup
 * files in order, which guarantees this runs before `setup.ts`'s own import
 * graph pulls in `authStore` — and `authStore` touches `localStorage` at module
 * load time, via zustand's `persist` rehydration.
 *
 * The fix: Node 25 exposes an experimental built-in `localStorage` global that
 * is inert unless the process was launched with `--localstorage-file` (it logs
 * "`--localstorage-file` was provided without a valid path" and hands back an
 * object with no `setItem`). Vitest's jsdom environment runs with
 * `window === globalThis`, so that inert built-in shadows jsdom's perfectly
 * good `localStorage`, and every `persist`-backed store throws
 * "storage.setItem is not a function".
 *
 * We only override a storage global that is actually broken, so on a Node
 * version without the built-in, jsdom's real implementation is left untouched.
 */

function createMemoryStorage(): Storage {
  let entries = new Map<string, string>()

  return {
    get length() {
      return entries.size
    },
    clear() {
      entries = new Map()
    },
    getItem(key: string) {
      return entries.has(String(key)) ? entries.get(String(key))! : null
    },
    key(index: number) {
      return Array.from(entries.keys())[index] ?? null
    },
    removeItem(key: string) {
      entries.delete(String(key))
    },
    setItem(key: string, value: string) {
      entries.set(String(key), String(value))
    },
  } as Storage
}

function isUsableStorage(name: 'localStorage' | 'sessionStorage'): boolean {
  try {
    const candidate = (globalThis as Partial<Record<typeof name, Storage>>)[name]
    return typeof candidate?.setItem === 'function' && typeof candidate?.getItem === 'function'
  } catch {
    // Some environments expose the global as a getter that throws outright.
    return false
  }
}

for (const name of ['localStorage', 'sessionStorage'] as const) {
  if (!isUsableStorage(name)) {
    Object.defineProperty(globalThis, name, {
      value: createMemoryStorage(),
      configurable: true,
      writable: true,
    })
  }
}
