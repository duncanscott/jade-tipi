import "./globals.css";
import type { Metadata } from "next";
import { ToolsProvider } from "@/components/layout/ToolsContext";
import LayoutWithTools from "@/components/layout/LayoutWithTools";
import Header from "@/components/layout/Header";

export const metadata: Metadata = {
  title: "Jade Tipi",
  description: "MongoDB Document Manager",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <ToolsProvider>
          <div className="shell">
            <Header />
            <LayoutWithTools>
              {children}
            </LayoutWithTools>
          </div>
        </ToolsProvider>
        <div id="portal" />
      </body>
    </html>
  );
}
