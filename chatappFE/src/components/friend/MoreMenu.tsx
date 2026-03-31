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
        onClick={() => setOpen(!open)}
        className="
          w-9 h-9
          flex items-center justify-center
          rounded
          hover:bg-gray-300
          transition
          text-lg
        "
      >
        ⋯
      </button>

      {open && (
        <div className="absolute right-0 mt-1 bg-white shadow rounded min-w-max z-10">
          <button
            onClick={() => {
              onRemove();
              setOpen(false);
            }}
            className="
              block px-4 py-2
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