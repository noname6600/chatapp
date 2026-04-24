import clsx from "clsx";

interface TypingDotsProps {
  className?: string;
}

export default function TypingDots({ className }: TypingDotsProps) {
  return (
    <>
      <div className={clsx("typing-dots flex items-center gap-1", className)}>
        <div className="typing-dot" style={{ animationDelay: "0ms" }} />
        <div className="typing-dot" style={{ animationDelay: "150ms" }} />
        <div className="typing-dot" style={{ animationDelay: "300ms" }} />
      </div>
      <style>
        {`
          @keyframes typingDotPulse {
            0%, 80%, 100% { transform: translateY(0); opacity: 0.35; }
            40% { transform: translateY(-2px); opacity: 1; }
          }

          .typing-dots .typing-dot {
            width: 6px;
            height: 6px;
            border-radius: 9999px;
            background: rgb(156 163 175);
            animation: typingDotPulse 900ms ease-in-out infinite;
          }
        `}
      </style>
    </>
  );
}
