import { forwardRef } from "react";
import clsx from "clsx";

interface AvatarImageProps {
  src: string;
  alt: string;
  size: number;
  onClick?: () => void;
  className?: string;
  imgClassName?: string;
  draggable?: boolean;
  testId?: string;
}

const AvatarImage = forwardRef<HTMLSpanElement, AvatarImageProps>(function AvatarImage(
  {
    src,
    alt,
    size,
    onClick,
    className,
    imgClassName,
    draggable = false,
    testId,
  },
  ref
) {
  return (
    <span
      ref={ref}
      data-testid={testId}
      onClick={onClick}
      className={clsx(
        "inline-flex shrink-0 overflow-hidden rounded-full bg-gray-100",
        onClick ? "cursor-pointer" : undefined,
        className
      )}
      style={{ width: size, height: size }}
    >
      <img
        src={src}
        alt={alt}
        draggable={draggable}
        className={clsx("block h-full w-full object-cover", imgClassName)}
      />
    </span>
  );
});

export default AvatarImage;