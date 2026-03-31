import React from "react";
import clsx from "clsx";

export interface AvatarProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  size?: "xs" | "sm" | "md" | "lg" | "xl";
  initials?: string;
  status?: "online" | "idle" | "dnd" | "offline";
}

const Avatar = React.forwardRef<HTMLDivElement, AvatarProps>(
  ({ size = "md", initials, status, alt, className, ...props }, ref) => {
    const sizeClasses = {
      xs: "w-6 h-6 text-xs",
      sm: "w-8 h-8 text-sm",
      md: "w-10 h-10 text-base",
      lg: "w-12 h-12 text-lg",
      xl: "w-16 h-16 text-xl",
    };

    const statusClasses = {
      online: "ring-2 ring-green-500",
      idle: "ring-2 ring-yellow-500",
      dnd: "ring-2 ring-red-500",
      offline: "ring-2 ring-gray-400",
    };

    return (
      <div
        ref={ref}
        className={clsx(
          "relative inline-flex items-center justify-center rounded-full bg-gray-200 font-semibold text-gray-900 overflow-hidden flex-shrink-0",
          sizeClasses[size],
          status && statusClasses[status],
          className
        )}
        {...props}
      >
        {props.src ? (
          <img
            src={props.src}
            alt={alt || "Avatar"}
            className="w-full h-full object-cover"
          />
        ) : (
          initials && <span>{initials}</span>
        )}
      </div>
    );
  }
);
Avatar.displayName = "Avatar";

export { Avatar };
