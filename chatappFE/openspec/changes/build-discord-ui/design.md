## Context

Current chat UI uses Tailwind with basic components. The app already has:
- Working message API with support for reactions, edits, deletes
- Zustand stores for chat and presence state
- WebSocket integration for real-time updates
- Existing React components that are functional but not polished

## Goals / Non-Goals

**Goals:**
- Create a modern, polished chat interface matching Discord's feel
- Support rich message editing with markdown and code blocks
- Implement emoji reactions with picker
- Add message reply functionality
- Show typing indicators and user presence
- Maintain light theme throughout
- Use existing backend APIs - no backend changes needed

**Non-Goals:**
- Voice/video calling (UI framework only)
- Message search (future)
- Message pinning (future)
- Threads (users requested reply only, not threading)
- Custom emojis

## Decisions

### Decision 1: Library Stack

**Decision**: Use TipTap + Emoji Mart + Radix UI + Tailwind

- **TipTap**: Prosemirror-based rich editor with markdown support
- **Emoji Mart**: Lightweight emoji picker with search and recents
- **Radix UI**: Headless components (dialog, dropdown, context menu) - style with Tailwind
- **Framer Motion**: Smooth animations for polish

**Rationale**: 
- TipTap handles markdown and code blocks without bloating HTML
- Radix UI stays unopinionated, works perfectly with Tailwind
- Emoji Mart is battle-tested and lightweight
- Already using Tailwind, so no style conflicts

**Alternatives**:
- Slate editor - too heavy for chat use case
- Material-UI - comes with full styling, conflicts with Tailwind control

### Decision 2: Component Structure

**Decision**: Organize by feature, not by type

```
src/components/chat/
├── MessageList.tsx           (show messages with reactions)
├── MessageItem.tsx           (individual message with actions)
├── MessageInput.tsx          (rich editor)
├── ReactionPicker.tsx        (emoji picker)
├── ReactionDisplay.tsx       (show reactions below message)
├── ReplyPreview.tsx          (quoted message)
├── TypingIndicator.tsx       (X is typing...)
├── UserPresenceIndicator.tsx (online status dot)
└── UserMentionMenu.tsx       (autocomplete dropdown)
```

**Rationale**: Chat features are cohesive, easier to find and maintain together

### Decision 3: Real-time Sync

**Decision**: Use existing WebSocket events with new handlers

- `messageEdited` event → update message in store
- `messageDeleted` event → mark deleted
- `reactionAdded`/`reactionRemoved` events → sync reactions
- `userTyping` event → show typing indicator
- Presence service already handles online status

**Rationale**: No backend changes needed, reuse existing infrastructure

### Decision 4: Message State Management

**Decision**: Store reactions, edits, typing in Zustand

```typescript
interface ChatStore {
  messages: Record<messageId, ChatMessage>
  typingUsers: Record<roomId, userId[]>
  selectedReplyMessage: ChatMessage | null
  
  addReaction(messageId, emoji)
  removeReaction(messageId, emoji)
  editMessage(messageId, content)
  deleteMessage(messageId)
  setReplyMessage(message)
  addTypingUser(roomId, userId)
}
```

### Decision 5: Theme

**Decision**: Light theme as primary, handle in Tailwind via CSS variables

- Background: `#f5f5f5` light gray
- Cards: White `#ffffff`
- Text: Dark gray `#1a1a1a`
- Accent: Blue for actions
- Borders: Subtle gray

Use Tailwind's `data-theme` or CSS variables for future dark mode toggle

## Risks / Trade-offs

**[Risk]** Rich editor adds bundle size
**[Mitigation]** TipTap is tree-shakeable (~50KB), lazy load if needed

**[Risk]** Too many components to build
**[Mitigation]** Build incrementally - Phase 1 covers most critical, Phase 2 adds polish

**[Risk]** Emoji picker might slow down on old devices
**[Mitigation]** Emoji Mart is optimized, use virtualization if needed

**[Trade-off]** Light theme only initially
**[Rationale]** Can add dark mode toggle later using CSS variables

## Migration Plan

1. **Phase 1**: Install dependencies, create basic components
2. **Phase 2**: Integrate rich editor, file preview, reactions
3. **Phase 3**: Add typing indicators, presence, message actions
4. **Phase 4**: Polish styling, animations, edge cases
5. **Phase 5**: Testing and refinement

Each phase is independent and can be tested separately.

## Open Questions

- Should we show "edited" indicator on edited messages?
- How many emoji reactions should we show before "more" button?
- Should deleted messages show as "(deleted)" or disappear entirely?
