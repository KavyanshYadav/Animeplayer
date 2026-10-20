import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

/** The themes of the application. */
export type ThemeName =
  | "default"
  | "light"
  | "amber"
  | "lavender"
  | "catppuccin"
  | "dracula"
  | "custom"
  | "system";

export interface ThemeDescription {
  value: ThemeName;
  label: string;
  /** The colour of the theme, for the picker. */
  swatch: string;
}

export const THEMES: ThemeDescription[] = [
  { value: "default", label: "Default", swatch: "hsl(38 92% 50%)" },
  { value: "light", label: "Light", swatch: "hsl(0 0% 92%)" },
  { value: "amber", label: "Amber", swatch: "hsl(38 92% 50%)" },
  { value: "lavender", label: "Lavender", swatch: "hsl(270 70% 60%)" },
  { value: "catppuccin", label: "Catppuccin", swatch: "hsl(23 92% 75%)" },
  { value: "dracula", label: "Dracula", swatch: "hsl(265 89% 78%)" },
  { value: "custom", label: "Custom", swatch: "var(--custom-500, #f5a524)" },
  { value: "system", label: "System", swatch: "var(--sys-accent, #f5a524)" },
];

/** The accent colours the picker offers. */
export const ACCENTS = [
  "#f5a524",
  "#8b5cf6",
  "#3b82f6",
  "#10b981",
  "#ef4444",
  "#ec4899",
  "#f43f5e",
  "#06b6d4",
  "#eab308",
  "#84cc16",
];

const THEME_KEY = "animeplayer.theme.v2";
const ACCENT_KEY = "animeplayer.accent";
const SYSTEM_ACCENT_KEY = "animeplayer.system-accent";

interface ThemeContextValue {
  theme: ThemeName;
  accent: string;
  systemAccent: string;
  setTheme: (theme: ThemeName) => void;
  setAccent: (accent: string) => void;
  setSystemAccent: (accent: string) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function read(key: string, fallback: string): string {
  try {
    return window.localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

/** The stored theme — but only if it is a theme we know. */
function readTheme(): ThemeName {
  const stored = read(THEME_KEY, "default");
  return THEMES.some((theme) => theme.value === stored) ? (stored as ThemeName) : "default";
}

/**
 * The theme of the application.
 *
 * The theme is a class on the root element — the same idea the tokens of the
 * reference design use: every theme redefines the same semantic tokens.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>(readTheme);
  const [accent, setAccentState] = useState(() => read(ACCENT_KEY, "#f5a524"));
  const [systemAccent, setSystemAccentState] = useState(() =>
    read(SYSTEM_ACCENT_KEY, "#f5a524"),
  );

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove(
      "theme-default",
      "theme-light",
      "theme-amber",
      "theme-lavender",
      "theme-catppuccin",
      "theme-dracula",
      "theme-custom",
      "theme-system",
    );
    root.classList.add(`theme-${theme}`);
    root.classList.add("dark");
  }, [theme]);

  useEffect(() => {
    document.documentElement.style.setProperty("--custom", accent);
  }, [accent]);

  useEffect(() => {
    document.documentElement.style.setProperty("--sys-accent", systemAccent);
  }, [systemAccent]);

  const setTheme = useCallback((next: ThemeName) => {
    setThemeState(next);
    try {
      window.localStorage.setItem(THEME_KEY, next);
    } catch {
      /* the theme is not remembered */
    }
  }, []);

  const setAccent = useCallback((next: string) => {
    setAccentState(next);
    try {
      window.localStorage.setItem(ACCENT_KEY, next);
    } catch {
      /* the accent is not remembered */
    }
  }, []);

  const setSystemAccent = useCallback((next: string) => {
    setSystemAccentState(next);
    try {
      window.localStorage.setItem(SYSTEM_ACCENT_KEY, next);
    } catch {
      /* the accent is not remembered */
    }
  }, []);

  const value = useMemo(
    () => ({ theme, accent, systemAccent, setTheme, setAccent, setSystemAccent }),
    [theme, accent, systemAccent, setTheme, setAccent, setSystemAccent],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/** The theme of the application. */
export function useTheme(): ThemeContextValue {
  const value = useContext(ThemeContext);
  if (!value) {
    throw new Error("useTheme must be used inside a ThemeProvider.");
  }
  return value;
}
