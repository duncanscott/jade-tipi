'use client';

import React, { createContext, useContext, useState, ReactNode, useEffect } from 'react';

interface ToolsContextValue {
  tools: ReactNode;
  setTools: (tools: ReactNode) => void;
}

const ToolsContext = createContext<ToolsContextValue | undefined>(undefined);

const defaultTools = (
  <section className="tool-section">
    <h2>Tools</h2>
    <p style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>
      No tools defined for this page
    </p>
  </section>
);

export function ToolsProvider({ children }: { children: ReactNode }) {
  const [tools, setTools] = useState<ReactNode>(defaultTools);

  return (
    <ToolsContext.Provider value={{ tools, setTools }}>
      {children}
    </ToolsContext.Provider>
  );
}

export function useToolsContext() {
  const context = useContext(ToolsContext);
  if (context === undefined) {
    throw new Error('useToolsContext must be used within a ToolsProvider');
  }
  return context;
}

/**
 * Set the Tools sidebar content from a page/component.
 * Pass a dependency list to control when the tools are refreshed to avoid infinite re-renders.
 * Example: useTools(<MyTools count={count} />, [count])
 */
export function useTools(toolsContent: ReactNode, deps: React.DependencyList = []) {
  const { setTools } = useToolsContext();

  useEffect(() => {
    setTools(toolsContent);
    return () => {
      // Restore a sensible default when the page unmounts
      setTools(defaultTools);
    };
    // Intentionally depend on caller-provided deps only to avoid loops
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
