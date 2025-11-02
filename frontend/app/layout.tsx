import "./globals.css";
import type { Metadata } from "next";
import { ToolsProvider } from "@/components/layout/ToolsContext";
import LayoutWithTools from "@/components/layout/LayoutWithTools";
import Header from "@/components/layout/Header";
import { SessionProvider } from "next-auth/react";
import { auth } from "@/auth";

export const metadata: Metadata = {
  title: "Jade Tipi",
  description: "MongoDB Document Manager",
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await auth();

  return (
    <html lang="en">
      <body>
        <SessionProvider session={session}>
          <ToolsProvider>
            <div className="shell">
              <Header />
              <LayoutWithTools>
                {children}
              </LayoutWithTools>
            </div>
          </ToolsProvider>
        </SessionProvider>
        <div id="portal" />
      </body>
    </html>
  );
}
