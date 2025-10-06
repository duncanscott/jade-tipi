"use client";

import React from "react";

type Props = {
    tools: React.ReactNode;
    children: React.ReactNode;
    /** Server + first client render width to avoid hydration mismatch */
    defaultWidth?: number;
    /** Min/Max width in px when expanded */
    min?: number;
    max?: number;
    /** Width in px when collapsed (0 = fully hidden) */
    collapsedWidth?: number;
    /** localStorage keys to persist */
    storageKey?: string;          // for width
    collapsedStorageKey?: string; // for collapsed state
};

export default function ResizableColumns({
                                             tools,
                                             children,
                                             defaultWidth = 320,
                                             min = 220,
                                             max = 800,
                                             collapsedWidth = 0,
                                             storageKey = "sidebarWidth",
                                             collapsedStorageKey = "sidebarCollapsed",
                                         }: Props) {
    const containerRef = React.useRef<HTMLDivElement | null>(null);
    const handleRef = React.useRef<HTMLDivElement | null>(null);
    const asideRef = React.useRef<HTMLElement | null>(null);
    const draggingRef = React.useRef(false);
    const pointerIdRef = React.useRef<number | null>(null);
    const lastExpandedRef = React.useRef<number>(defaultWidth);

    // Render same width on server and initial client paint
    const [width, setWidth] = React.useState<number>(defaultWidth);
    const [collapsed, setCollapsed] = React.useState(false);

    // After mount, hydrate from localStorage (if available)
    React.useEffect(() => {
        try {
            const savedCollapsed = window.localStorage.getItem(collapsedStorageKey);
            if (savedCollapsed === "true") setCollapsed(true);
            const savedWidth = window.localStorage.getItem(storageKey);
            if (savedWidth) {
                const n = Number(savedWidth);
                if (!Number.isNaN(n)) {
                    const clamped = clamp(n, min, max);
                    setWidth(clamped);
                    lastExpandedRef.current = clamped;
                }
            }
        } catch {
            /* ignore */
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Keep DOM inert attribute in sync when collapsed
    React.useEffect(() => {
        const el = asideRef.current;
        if (!el) return;
        if (collapsed) {
            el.setAttribute('inert', '');
        } else {
            el.removeAttribute('inert');
        }
    }, [collapsed]);

    // Persist width and collapsed on change (post-mount)
    React.useEffect(() => {
        try {
            window.localStorage.setItem(storageKey, String(width));
        } catch {/* ignore */}
    }, [width, storageKey]);

    React.useEffect(() => {
        try {
            window.localStorage.setItem(collapsedStorageKey, String(collapsed));
        } catch {/* ignore */}
    }, [collapsed, collapsedStorageKey]);

    // Helper for position -> width
    const updateFromClientX = React.useCallback(
        (clientX: number) => {
            const el = containerRef.current;
            if (!el) return;
            const rect = el.getBoundingClientRect();
            const x = clientX - rect.left;
            const next = clamp(x, min, max);
            setWidth(next);
            lastExpandedRef.current = next; // remember last expanded width
        },
        [min, max]
    );

    // Start drag via Pointer Events
    const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
        // Only left button for mouse; touch/pen are fine
        if (e.button !== 0 && e.pointerType === "mouse") return;

        draggingRef.current = true;
        pointerIdRef.current = e.pointerId;

        try {
            handleRef.current?.setPointerCapture(e.pointerId);
        } catch {/* ignore */}

        document.body.style.cursor = "col-resize";
        document.body.style.userSelect = "none";

        // If collapsed, expand first (so drag works immediately)
        if (collapsed) {
            setCollapsed(false);
            setWidth(lastExpandedRef.current);
        }
        updateFromClientX(e.clientX);
    };

    // Global listeners for pointer move/up/cancel
    React.useEffect(() => {
        function handlePointerMove(e: PointerEvent) {
            if (!draggingRef.current) return;
            e.preventDefault?.();
            updateFromClientX(e.clientX);
        }

        function stopDrag() {
            if (!draggingRef.current) return;
            draggingRef.current = false;

            const id = pointerIdRef.current;
            if (id != null) {
                try {
                    handleRef.current?.releasePointerCapture(id);
                } catch {/* ignore */}
            }
            pointerIdRef.current = null;

            document.body.style.cursor = "";
            document.body.style.userSelect = "";
        }

        window.addEventListener("pointermove", handlePointerMove, { passive: false });
        window.addEventListener("pointerup", stopDrag);
        window.addEventListener("pointercancel", stopDrag);

        return () => {
            window.removeEventListener("pointermove", handlePointerMove);
            window.removeEventListener("pointerup", stopDrag);
            window.removeEventListener("pointercancel", stopDrag);
        };
    }, [updateFromClientX]);

    // On unmount, ensure body styles are restored
    React.useEffect(() => {
        return () => {
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        };
    }, []);

    // Keyboard resizing + ESC to cancel
    function onKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
        const step = e.shiftKey ? 20 : 10;
        if (e.key === "ArrowLeft") {
            e.preventDefault();
            const next = clamp((collapsed ? lastExpandedRef.current : width) - step, min, max);
            setCollapsed(false);
            setWidth(next);
            lastExpandedRef.current = next;
        } else if (e.key === "ArrowRight") {
            e.preventDefault();
            const next = clamp((collapsed ? lastExpandedRef.current : width) + step, min, max);
            setCollapsed(false);
            setWidth(next);
            lastExpandedRef.current = next;
        } else if (e.key === "Home") {
            e.preventDefault();
            setCollapsed(false);
            setWidth(min);
            lastExpandedRef.current = min;
        } else if (e.key === "End") {
            e.preventDefault();
            setCollapsed(false);
            setWidth(max);
            lastExpandedRef.current = max;
        } else if (e.key === "Escape") {
            e.preventDefault();
            // End any active drag
            if (draggingRef.current) {
                draggingRef.current = false;
                const id = pointerIdRef.current;
                if (id != null) {
                    try {
                        handleRef.current?.releasePointerCapture(id);
                    } catch {/* ignore */}
                }
                pointerIdRef.current = null;
                document.body.style.cursor = "";
                document.body.style.userSelect = "";
            }
        }
    }

    // Toggle collapse/expand (only on the handle button)
    const toggleCollapsed = (e?: React.SyntheticEvent) => {
        e?.stopPropagation?.();
        setCollapsed((c) => {
            if (c) {
                // expanding: restore last known expanded width
                setWidth(lastExpandedRef.current);
                return false;
            } else {
                // collapsing: remember current width for future restore
                lastExpandedRef.current = clamp(width, min, max);
                setWidth(collapsedWidth);
                return true;
            }
        });
    };

    // Effective width sent to CSS var
    const effectivePx = collapsed ? collapsedWidth : width;

    return (
        <div
            ref={containerRef}
            className="content"
            data-collapsed={collapsed ? "true" : "false"}
            // CSS owns the grid; we only provide the current width value
            style={{ "--sidebar-px": `${effectivePx}px` } as React.CSSProperties}
        >
            <aside
                ref={asideRef}
                className={`tools${collapsed ? " is-collapsed" : ""}`}
                aria-label="Tools sidebar"
                aria-hidden={collapsed}
                id="tools-panel"
            >
                {tools}
            </aside>

            {/* Drag handle between columns, with the ONLY collapse toggle */}
            <div
                ref={handleRef}
                className="resize-handle"
                role="separator"
                aria-orientation="vertical"
                aria-label="Resize sidebar"
                aria-valuemin={min}
                aria-valuemax={max}
                aria-valuenow={Math.round(effectivePx)}
                tabIndex={0}
                onPointerDown={onPointerDown}
                onKeyDown={onKeyDown}
            >
                <button
                    type="button"
                    className="collapse-btn handle-btn"
                    onClick={toggleCollapsed}
                    onPointerDown={(e) => e.stopPropagation()} /* prevent drag start */
                    aria-pressed={collapsed}
                    aria-expanded={!collapsed}
                    aria-controls="tools-panel"
                    title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
                    aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
                >
                    {/* Slightly larger, thicker chevrons for better visibility */}
                    {collapsed ? (
                        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                            <polyline points="6,4 10,8 6,12" stroke="currentColor" strokeWidth="2.25" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                    ) : (
                        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                            <polyline points="10,4 6,8 10,12" stroke="currentColor" strokeWidth="2.25" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                    )}
                </button>
            </div>

            <main className="main" role="main">
                {children}
            </main>
        </div>
    );
}

function clamp(n: number, min: number, max: number) {
    return Math.min(Math.max(n, min), max);
}
