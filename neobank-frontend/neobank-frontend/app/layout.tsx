import type { Metadata } from "next";
import { Syne, DM_Sans } from "next/font/google";
import { Toaster } from "react-hot-toast";
import "./globals.css";

const syne = Syne({
  subsets: ["latin"],
  variable: "--font-syne",
  weight: ["400", "500", "600", "700", "800"],
});

const dmSans = DM_Sans({
  subsets: ["latin"],
  variable: "--font-dm-sans",
  weight: ["300", "400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "NeoBank — Banca digital moderna",
  description: "Tu banco digital con transferencias instantáneas, seguridad avanzada y más.",
  icons: { icon: "/favicon.ico" },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es-MX" className={`${syne.variable} ${dmSans.variable}`}>
      <body>
        {children}
        <Toaster
          position="top-right"
          toastOptions={{
            style: {
              background: "#13131f",
              color: "#f1f5f9",
              border: "1px solid #1e1e30",
              borderRadius: "12px",
              fontFamily: "var(--font-dm-sans)",
            },
            success: { iconTheme: { primary: "#10b981", secondary: "#13131f" } },
            error:   { iconTheme: { primary: "#ef4444", secondary: "#13131f" } },
          }}
        />
      </body>
    </html>
  );
}
