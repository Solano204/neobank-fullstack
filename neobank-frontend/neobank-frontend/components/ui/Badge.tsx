import { cn } from "@/lib/utils";

type Variant = "success" | "pending" | "failed" | "info" | "warning";
interface BadgeProps { children: React.ReactNode; variant?: Variant; className?: string; }

const variants: Record<Variant, string> = {
  success: "status-success", pending: "status-pending", failed: "status-failed",
  info:    "status-info",    warning: "status-pending",
};

export default function Badge({ children, variant = "info", className }: BadgeProps) {
  return <span className={cn("status-badge", variants[variant], className)}>{children}</span>;
}
