import DOMPurify from 'dompurify'
import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

function normalizeModelMarkdown(content) {
  return String(content || '')
    .replace(/\r\n?/g, '\n')
    // Some persisted SSE responses contain JSON-style newline escapes.
    .replace(/\\n/g, '\n')
    // Normalize escaped heading markers returned by some providers.
    .replace(/^(\s{0,3})\\(#{1,6})(?=\s|\S)/gm, '$1$2')
    // A few providers place headings directly after prose; give marked a block boundary.
    .replace(/([^\n])\n(\s{0,3}#{1,6}\s+)/g, '$1\n\n$2')
    // Chinese models occasionally omit the required space after a heading marker.
    .replace(/^(\s{0,3}#{1,6})(?=\S)/gm, '$1 ')
    // Split a heading and an immediately following numbered bold item.
    .replace(/^(#{1,6}\s+[^\n]*?)(\*\*\s*\d+[.、])/gm, '$1\n\n$2')
    .replace(/\*\*([^\n*]+?)\s+\*\*/g, '**$1**')
}

export function renderMarkdown(content) {
  const html = marked.parse(normalizeModelMarkdown(content))
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'form'],
    FORBID_ATTR: ['style']
  })
}
