import clsx from "clsx";

interface TypingDotsProps {
  className?: string;
}

export default function TypingDots({ className }: TypingDotsProps) {
  return (
    <div className={clsx("flex items-center gap-1", className)}>
      <div 
        className="w-1.5 h-1.5 rounded-full bg-gray-400 animate-bounce" 
        style={{ animationDelay: "0ms" }} 
      />
      <div 
        className="w-1.5 h-1.5 rounded-full bg-gray-400 animate-bounce" 
        style={{ animationDelay: "150ms" }} 
      />
      <div 
        className="w-1.5 h-1.5 rounded-full bg-gray-400 animate-bounce" 
        style={{ animationDelay: "300ms" }} 
      />
    </div>
  );
}
