import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatMXN(amount: number): string {
  return new Intl.NumberFormat("es-MX", {
    style: "currency", currency: "MXN", minimumFractionDigits: 2,
  }).format(amount);
}

export function formatCLABE(clabe: string): string {
  return clabe.replace(/(.{4})/g, "$1 ").trim();
}

export function formatDate(date: string | number): string {
  return new Intl.DateTimeFormat("es-MX", {
    day: "2-digit", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  }).format(new Date(date));
}

export function formatDateShort(date: string): string {
  return new Intl.DateTimeFormat("es-MX", {
    day: "2-digit", month: "short",
  }).format(new Date(date));
}

export function maskAccount(account: string): string {
  if (!account) return "";
  return `•••• ${account.slice(-4)}`;
}

export function getInitials(name: string): string {
  return name.split(" ").slice(0, 2).map(n => n[0]).join("").toUpperCase();
}

export function getTransactionSign(type: string, isSender: boolean): string {
  if (type === "DEPOSIT") return "+";
  if (type === "WITHDRAWAL") return "-";
  return isSender ? "-" : "+";
}
