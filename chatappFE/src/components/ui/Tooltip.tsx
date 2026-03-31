import React, { useState } from "react";
import clsx from "clsx";

export interface TooltipProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'content'> {
  content: React.ReactNode;
  position?: "top" | "right" | "bottom" | "left";
  delay?: number;
}

const Tooltip = React.forwardRef<HTMLDivElement, TooltipProps>(
  (
    { content, position = "top", delay = 200, className, children, ...props },
    ref
  ) => {
    const [isVisible, setIsVisible] = useState(false);
    const [timeoutId, setTimeoutId] = useState<ReturnType<typeof setTimeout> | null>(null);

    const handleMouseEnter = () => {
      const id = setTimeout(() => setIsVisible(true), delay);
      setTimeoutId(id);
    };

    const handleMouseLeave = () => {
      if (timeoutId) clearTimeout(timeoutId);
      setIsVisible(false);
    };

    const positionClasses = {
      top: "bottom-full left-1/2 -translate-x-1/2 mb-2",
      right: "left-full top-1/2 -translate-y-1/2 ml-2",
      bottom: "top-full left-1/2 -translate-x-1/2 mt-2",
      left: "right-full top-1/2 -translate-y-1/2 mr-2",
    };

    const arrowClasses = {
      top: "bottom-[-4px] left-1/2 -translate-x-1/2 border-t-gray-900",
      right: "left-[-4px] top-1/2 -translate-y-1/2 border-r-gray-900",
      bottom: "top-[-4px] left-1/2 -translate-x-1/2 border-b-gray-900",
      left: "right-[-4px] top-1/2 -translate-y-1/2 border-l-gray-900",
    };

    return (
      <div
        ref={ref}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        className="relative inline-block"
        {...props}
      >
        {children}

        {isVisible && (
          <div
            className={clsx(
              "absolute z-50 whitespace-nowrap rounded bg-gray-900 px-2 py-1 text-xs text-white pointer-events-none animate-fade-in",
              positionClasses[position]
            )}
          >
            {content}
            <div
              className={clsx(
                "absolute w-0 h-0 border-4 border-transparent",
                arrowClasses[position]
              )}
            />
          </div>
        )}
      </div>
    );
  }
);
Tooltip.displayName = "Tooltip";

export { Tooltip };
