import { useState, useRef, useEffect } from "react";
import { Smile } from "lucide-react";
import clsx from "clsx";
import { Picker } from "emoji-mart";

interface EmojiPickerProps {
  onEmojiSelect?: (emoji: string) => void;
  disabled?: boolean;
  className?: string;
  triggerClassName?: string;
}

export default function EmojiPicker({
  onEmojiSelect,
  disabled = false,
  className,
  triggerClassName,
}: EmojiPickerProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const pickerRef = useRef<HTMLDivElement>(null);

  // Initialize picker and close on click outside
  useEffect(() => {
    if (!isOpen || !pickerRef.current) return;

    let picker: Picker | null = null;

    try {
      picker = new Picker({
        onEmojiSelect: (emoji: any) => {
          onEmojiSelect?.(emoji.native);
          setIsOpen(false);
        },
        theme: "light",
        previewPosition: "none",
        skinTonePosition: "search",
        searchPosition: "top",
      });

      pickerRef.current.appendChild(picker as any);
    } catch (error) {
      console.error("Failed to initialize emoji picker:", error);
    }

    const handleClickOutside = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node) &&
        !triggerRef.current?.contains(e.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      if (picker && pickerRef.current) {
        try {
          pickerRef.current.innerHTML = "";
        } catch (e) {
          // Cleanup error, ignore
        }
      }
    };
  }, [isOpen, onEmojiSelect]);

  return (
    <div className={clsx("relative", className)}>
      <button
        ref={triggerRef}
        onClick={() => setIsOpen(!isOpen)}
        disabled={disabled}
        className={clsx(
          "p-1.5 rounded hover:bg-gray-100 transition disabled:opacity-50 disabled:cursor-not-allowed",
          triggerClassName
        )}
        title="Add reaction"
        type="button"
      >
        <Smile size={16} />
      </button>

      {isOpen && (
        <div
          ref={containerRef}
          className="absolute bottom-full right-0 mb-2 z-50 shadow-lg rounded-lg"
        >
          <div ref={pickerRef} />
        </div>
      )}
    </div>
  );
}
