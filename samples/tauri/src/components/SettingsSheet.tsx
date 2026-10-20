import { Check } from "lucide-react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ACCENTS, THEMES, useTheme } from "@/lib/theme";
import { cn } from "@/lib/utils";

interface SettingsSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** The settings of the application — the theme and the accent colour. */
export function SettingsSheet({ open, onOpenChange }: SettingsSheetProps) {
  const { theme, accent, systemAccent, setTheme, setAccent, setSystemAccent } = useTheme();

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>The settings</DialogTitle>
          <DialogDescription>
            The theme of the application and the colour of the highlights.
          </DialogDescription>
        </DialogHeader>

        <section className="space-y-3">
          <h3 className="text-sm font-bold">The theme</h3>
          <div className="grid grid-cols-4 gap-2">
            {THEMES.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => setTheme(item.value)}
                className={cn(
                  "group relative flex flex-col items-center gap-2 rounded-lg border p-2 transition-colors",
                  theme === item.value
                    ? "border-primary bg-accent/40"
                    : "border-border hover:border-border/80 hover:bg-accent/30",
                )}
              >
                <span
                  className="size-7 rounded-full border border-border/60"
                  style={{ backgroundColor: item.swatch }}
                />
                <span className="text-[11px] text-muted-foreground group-hover:text-foreground">
                  {item.label}
                </span>
                {theme === item.value && (
                  <Check className="absolute right-1.5 top-1.5 size-3.5 text-primary" />
                )}
              </button>
            ))}
          </div>
        </section>

        <section className="space-y-3">
          <h3 className="text-sm font-bold">The accent colour</h3>
          <div className="flex flex-wrap gap-2">
            {ACCENTS.map((colour) => (
              <button
                key={colour}
                type="button"
                onClick={() => setAccent(colour)}
                title={colour}
                className={cn(
                  "size-7 rounded-full border-2 transition-transform",
                  accent === colour
                    ? "border-foreground scale-110"
                    : "border-transparent hover:scale-110",
                )}
                style={{ backgroundColor: colour }}
              />
            ))}
            <label className="relative size-7 cursor-pointer overflow-hidden rounded-full border-2 border-transparent hover:scale-110">
              <input
                type="color"
                value={accent}
                onChange={(event) => setAccent(event.target.value)}
                className="absolute inset-0 size-full cursor-pointer opacity-0"
              />
              <span
                className="block size-full"
                style={{
                  background: `conic-gradient(from 0deg, red, yellow, lime, aqua, blue, magenta, red)`,
                }}
              />
            </label>
          </div>
          <p className="text-xs text-muted-foreground">
            The accent colour drives the <code className="rounded bg-muted px-1">custom</code>{" "}
            theme and the highlights.
          </p>
        </section>

        {theme === "system" && (
          <section className="space-y-3">
            <h3 className="text-sm font-bold">The accent of the desktop</h3>
            <div className="flex flex-wrap gap-2">
              {ACCENTS.map((colour) => (
                <button
                  key={colour}
                  type="button"
                  onClick={() => setSystemAccent(colour)}
                  title={colour}
                  className={cn(
                    "size-7 rounded-full border-2 transition-transform",
                    systemAccent === colour
                      ? "border-foreground scale-110"
                      : "border-transparent hover:scale-110",
                  )}
                  style={{ backgroundColor: colour }}
                />
              ))}
            </div>
          </section>
        )}
      </DialogContent>
    </Dialog>
  );
}
