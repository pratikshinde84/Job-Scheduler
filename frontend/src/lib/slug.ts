/**
 * Convert any string to a URL-safe slug.
 * "My App 2" → "my-app-2"
 */
export function toSlug(name: string): string {
  return name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/**
 * Find an item in a list whose name slug matches the URL param.
 * Falls back to exact name match if no slug match found.
 */
export function findBySlug<T extends { name: string }>(
  items: T[],
  slug: string
): T | undefined {
  return (
    items.find(i => toSlug(i.name) === slug) ??
    items.find(i => i.name === slug)
  )
}
