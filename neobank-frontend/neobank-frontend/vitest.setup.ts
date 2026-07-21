export {};

// Only relevant for component tests (`// @vitest-environment jsdom`) - plain
// lib/api unit tests run in the default "node" environment, where `window`
// is undefined.
if (typeof window !== "undefined") {
  await import("@testing-library/jest-dom/vitest");
  if (!window.matchMedia) {
    window.matchMedia = (query: string) =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList;
  }
}
