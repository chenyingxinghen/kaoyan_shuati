import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";

export default function CustomSelect({
  value,
  onChange,
  options = [],
  disabled = false,
  minWidth = 120,
  compact = false,
  ariaLabel,
  placeholder = "请选择",
}) {
  const [open, setOpen] = useState(false);
  const [menuStyle, setMenuStyle] = useState(null);
  const rootRef = useRef(null);
  const menuRef = useRef(null);

  const selected = useMemo(
    () => options.find((item) => item.value === value) || null,
    [options, value]
  );

  const updateMenuPosition = () => {
    const trigger = rootRef.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    const maxWidth = Math.min(420, window.innerWidth - 16);
    const width = Math.min(Math.max(rect.width, compact ? minWidth : rect.width), maxWidth);
    const spaceBelow = window.innerHeight - rect.bottom - 8;
    const spaceAbove = rect.top - 8;
    const preferBelow = spaceBelow >= 160 || spaceBelow >= spaceAbove;
    const maxHeight = Math.min(240, preferBelow ? spaceBelow : spaceAbove);
    const left = Math.min(Math.max(8, rect.left), window.innerWidth - width - 8);
    setMenuStyle({
      position: "fixed",
      left,
      width,
      maxHeight: Math.max(120, maxHeight),
      zIndex: 9999,
      ...(preferBelow
        ? { top: rect.bottom + 4 }
        : { bottom: window.innerHeight - rect.top + 4 }),
    });
  };

  useLayoutEffect(() => {
    if (!open) return undefined;
    updateMenuPosition();
    const onReposition = () => updateMenuPosition();
    window.addEventListener("resize", onReposition);
    window.addEventListener("scroll", onReposition, true);
    return () => {
      window.removeEventListener("resize", onReposition);
      window.removeEventListener("scroll", onReposition, true);
    };
  }, [open, options.length, compact, minWidth]);

  useEffect(() => {
    if (!open) return undefined;
    const handleClickOutside = (event) => {
      const inRoot = rootRef.current?.contains(event.target);
      const inMenu = menuRef.current?.contains(event.target);
      if (!inRoot && !inMenu) setOpen(false);
    };
    const handleEscape = (event) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  const handleSelect = (nextValue) => {
    if (disabled) return;
    onChange?.(nextValue);
    setOpen(false);
  };

  return (
    <div
      ref={rootRef}
      className={`oe-select ${compact ? "is-compact" : ""} ${open ? "is-open" : ""} ${disabled ? "is-disabled" : ""}`}
      style={compact ? { minWidth } : { minWidth, width: "100%", maxWidth: "100%" }}
    >
      <button
        type="button"
        className="oe-select-trigger"
        disabled={disabled}
        aria-label={ariaLabel}
        aria-expanded={open}
        title={selected?.label || placeholder}
        onClick={() => !disabled && setOpen((prev) => !prev)}
      >
        <span className={`oe-select-value${!selected ? " is-placeholder" : ""}`}>
          {selected?.label || placeholder}
        </span>
        <svg className="oe-select-caret" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>

      {open && menuStyle && createPortal(
        <div ref={menuRef} className="oe-select-menu is-portal" role="listbox" style={menuStyle}>
          {options.map((item) => {
            const active = item.value === value;
            return (
              <button
                key={item.value}
                type="button"
                role="option"
                aria-selected={active}
                className={`oe-select-option ${active ? "is-active" : ""}`}
                title={item.label}
                onClick={() => handleSelect(item.value)}
              >
                <span className="oe-select-option-label">{item.label}</span>
                {active && <span className="oe-select-check">✓</span>}
              </button>
            );
          })}
        </div>,
        document.body
      )}
    </div>
  );
}
