import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Placeholder from "@tiptap/extension-placeholder";
import {
  Bold,
  Italic,
  Code,
  List,
  ListOrdered,
  Heading2,
  Quote,
  Undo2,
  Redo2,
} from "lucide-react";
import clsx from "clsx";

interface RichEditorProps {
  placeholder?: string;
  value?: string;
  onChange?: (content: string) => void;
  onSubmit?: (content: string) => void;
  onKeyDown?: (e: React.KeyboardEvent) => void;
  disabled?: boolean;
  maxLength?: number;
}

export default function RichEditor({
  placeholder = "Type a message...",
  value = "",
  onChange,
  onSubmit,
  onKeyDown,
  disabled = false,
  maxLength,
}: RichEditorProps) {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2] },
      }),
      Placeholder.configure({
        placeholder,
      }),
    ],
    content: value,
    onUpdate: ({ editor }) => {
      const html = editor.getHTML();
      onChange?.(html);
    },
    editorProps: {
      attributes: {
        class: clsx(
          "prose prose-sm max-w-none focus:outline-none",
          "min-h-12 max-h-32 p-3 bg-white rounded-lg",
          "border border-gray-300 border-b-2 border-b-gray-400",
          "text-gray-900 placeholder-gray-400",
          "resize-none overflow-y-auto",
          "disabled:bg-gray-50 disabled:opacity-50"
        ),
      },
      handleKeyDown: (_, event) => {
        // Call custom onKeyDown handler
        if (onKeyDown) {
          const e = event as unknown as React.KeyboardEvent;
          onKeyDown(e);
          if (e.defaultPrevented) return true;
        }

        // Submit on Cmd/Ctrl + Enter
        if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
          const html = editor?.getHTML() || "";
          if (html && html !== "<p></p>" && onSubmit) {
            onSubmit(html);
            editor?.commands.clearContent();
          }
          return true;
        }
        return false;
      },
    },
  });

  if (!editor) return null;

  const toggleMark = (
    _: string,
    isActive: boolean,
    icon: React.ReactNode,
    label: string,
    onClick: () => void
  ) => (
    <button
      onClick={onClick}
      disabled={disabled}
      className={clsx(
        "p-2 rounded hover:bg-gray-100 transition",
        isActive && "bg-blue-100 text-blue-600"
      )}
      title={label}
      type="button"
    >
      {icon}
    </button>
  );

  return (
    <div className="space-y-2 bg-white border border-gray-200 rounded-lg p-3">
      {/* Toolbar */}
      <div className="flex gap-1 pb-2 border-b border-gray-200">
        {toggleMark(
          "bold",
          editor.isActive("bold"),
          <Bold size={18} />,
          "Bold",
          () => editor.chain().focus().toggleBold().run()
        )}
        {toggleMark(
          "italic",
          editor.isActive("italic"),
          <Italic size={18} />,
          "Italic",
          () => editor.chain().focus().toggleItalic().run()
        )}
        {toggleMark(
          "code",
          editor.isActive("code"),
          <Code size={18} />,
          "Code",
          () => editor.chain().focus().toggleCode().run()
        )}
        
        <div className="w-px bg-gray-300" />

        {toggleMark(
          "heading",
          editor.isActive("heading", { level: 2 }),
          <Heading2 size={18} />,
          "Heading",
          () => editor.chain().focus().toggleHeading({ level: 2 }).run()
        )}
        {toggleMark(
          "bulletList",
          editor.isActive("bulletList"),
          <List size={18} />,
          "Bullet List",
          () => editor.chain().focus().toggleBulletList().run()
        )}
        {toggleMark(
          "orderedList",
          editor.isActive("orderedList"),
          <ListOrdered size={18} />,
          "Ordered List",
          () => editor.chain().focus().toggleOrderedList().run()
        )}
        {toggleMark(
          "blockquote",
          editor.isActive("blockquote"),
          <Quote size={18} />,
          "Quote",
          () => editor.chain().focus().toggleBlockquote().run()
        )}

        <div className="w-px bg-gray-300" />

        <button
          onClick={() => editor.chain().focus().undo().run()}
          disabled={!editor.can().undo() || disabled}
          className="p-2 rounded hover:bg-gray-100 transition disabled:opacity-50"
          title="Undo"
          type="button"
        >
          <Undo2 size={18} />
        </button>
        <button
          onClick={() => editor.chain().focus().redo().run()}
          disabled={!editor.can().redo() || disabled}
          className="p-2 rounded hover:bg-gray-100 transition disabled:opacity-50"
          title="Redo"
          type="button"
        >
          <Redo2 size={18} />
        </button>
      </div>

      {/* Editor */}
      <EditorContent editor={editor} disabled={disabled} />

      {/* Footer with character count and submit hint */}
      <div className="flex items-center justify-between text-xs text-gray-500 pt-2">
        <span>
          {maxLength && `${editor.getHTML().length} / ${maxLength}`}
        </span>
        <span>Ctrl+Enter to send</span>
      </div>
    </div>
  );
}
