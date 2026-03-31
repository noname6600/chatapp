import type { MessageBlock } from "../../types/message"
import { formatMessageTimeShort } from "../../utils/messageTimestamp"
import { getRenderableBlocks } from "./MessageBlocks"

interface EditedIndicatorProps {
  editedAt: string | null
  blocks?: MessageBlock[]
}

export default function EditedIndicator({
  editedAt,
  blocks,
}: EditedIndicatorProps) {
  if (!editedAt) return null

  const renderableBlocks = blocks ? getRenderableBlocks(blocks) : []
  const hasBlocksContext = Array.isArray(blocks)
  const isSingleTextBlock =
    renderableBlocks.length === 1 && renderableBlocks[0].type === "TEXT"
  const inline = !hasBlocksContext || isSingleTextBlock

  return (
    <span
      className={
        inline
          ? "ml-1 inline-flex rounded border border-slate-200 bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500 whitespace-nowrap align-baseline"
          : "mt-1 inline-flex w-fit rounded border border-slate-200 bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500"
      }
    >
      edited {formatMessageTimeShort(editedAt)}
    </span>
  )
}
