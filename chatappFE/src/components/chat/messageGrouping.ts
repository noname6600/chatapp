import type { ChatMessage } from "../../types/message"

export interface MessageGroup {
  messages: ChatMessage[]
}

export function groupMessages(messages: ChatMessage[]): MessageGroup[] {
  if (!messages.length) return []

  const groups: MessageGroup[] = []
  let currentGroup: ChatMessage[] = []

  for (let i = 0; i < messages.length; i++) {
    const current = messages[i]
    const previous = i > 0 ? messages[i - 1] : null

    const shouldGroup =
      previous !== null &&
      current.type !== "SYSTEM" &&
      previous.type !== "SYSTEM" &&
      current.senderId === previous.senderId &&
      !(current.attachments?.length || previous.attachments?.length) &&
      new Date(current.createdAt).getTime() -
        new Date(previous.createdAt).getTime() <
        2 * 60 * 1000

    if (!shouldGroup && currentGroup.length) {
      groups.push({ messages: currentGroup })
      currentGroup = []
    }

    currentGroup.push(current)
  }

  if (currentGroup.length) {
    groups.push({ messages: currentGroup })
  }

  return groups
}
