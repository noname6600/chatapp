/**
 * Convert HTML content from TipTap to plain text
 */
export function htmlToPlainText(html: string): string {
  if (!html) return "";
  
  // Create a temporary div to parse HTML
  const div = document.createElement("div");
  div.innerHTML = html;
  
  let text = "";
  
  // Recursively extract text from all nodes
  const traverse = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent || "";
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      const element = node as Element;
      
      // Add line breaks for block elements
      if (
        element.tagName === "P" ||
        element.tagName === "DIV" ||
        element.tagName === "LI"
      ) {
        if (text && !text.endsWith("\n")) {
          text += "\n";
        }
      }
      
      for (const child of node.childNodes) {
        traverse(child);
      }
    }
  };
  
  traverse(div);
  return text.trim();
}

/**
 * Extract mentions (@username) from HTML content
 */
export function extractMentions(html: string): string[] {
  const text = htmlToPlainText(html);
  const mentionRegex = /@(\w+)/g;
  const matches = text.match(mentionRegex);
  return matches ? matches.map((m) => m.substring(1)) : [];
}

/**
 * Convert HTML to markdown for storage/display
 */
export function htmlToMarkdown(html: string): string {
  let markdown = htmlToPlainText(html);
  
  // Convert common patterns
  markdown = markdown
    .replace(/<strong>(.*?)<\/strong>/g, "**$1**")
    .replace(/<em>(.*?)<\/em>/g, "*$1*")
    .replace(/<code>(.*?)<\/code>/g, "`$1`")
    .replace(/<h2>(.*?)<\/h2>/g, "## $1")
    .replace(/<blockquote>(.*?)<\/blockquote>/g, "> $1");
  
  return markdown;
}

/**
 * Check if HTML content is empty
 */
export function isEmptyHTML(html: string): boolean {
  return !html || html === "<p></p>" || htmlToPlainText(html).length === 0;
}

/**
 * Get plain text length from HTML
 */
export function getContentLength(html: string): number {
  return htmlToPlainText(html).length;
}
