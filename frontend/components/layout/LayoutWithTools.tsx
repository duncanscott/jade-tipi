'use client';

import ResizableColumns from "./ResizableColumns";
import { useToolsContext } from "./ToolsContext";

interface LayoutWithToolsProps {
  children: React.ReactNode;
}

export default function LayoutWithTools({ children }: LayoutWithToolsProps) {
  const { tools } = useToolsContext();

  return (
    <ResizableColumns tools={tools}>
      {children}
    </ResizableColumns>
  );
}
