import { cn } from "@/lib/utils";

interface CardProps { children: React.ReactNode; className?: string; onClick?: () => void; }

export default function Card({ children, className, onClick }: CardProps) {
  return (
    <div
      onClick={onClick}
      className={cn(
        "card-neo p-5",
        onClick && "cursor-pointer hover:border-blue-500/30 transition-colors",
        className
      )}
    >
      {children}
    </div>
  );
}
