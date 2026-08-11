import DOMPurify from 'dompurify'
import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

export function renderMarkdown(content) {
  // Preserve the model's Markdown verbatim; formatting requirements belong in the system prompt.
  const html = marked.parse(String(content || '').replace(/\r\n?/g, '\n'))
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'form'],
    FORBID_ATTR: ['style']
  })
}
