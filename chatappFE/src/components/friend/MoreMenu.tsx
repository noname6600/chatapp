import { useEffect, useRef, useState } from "react";

export function MoreMenu({ onRemove }: { onRemove: () => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={(e) => {
          e.stopPropagation();
          setOpen(!open);
        }}
        aria-label="More actions"
        className="
          w-9 h-9
          flex items-center justify-center
          rounded-lg border border-gray-200
          hover:bg-gray-100
          transition
          text-lg
        "
      >
        ⋯
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-1 min-w-[11rem] overflow-hidden rounded-xl border border-gray-200 bg-white py-1 shadow-lg">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
              setOpen(false);
            }}
            className="
              block w-full px-4 py-2 text-left
              text-red-500
              hover:bg-gray-100
              whitespace-nowrap
            "
          >
            Remove Friend
          </button>
        </div>
      )}
    </div>
  );
}