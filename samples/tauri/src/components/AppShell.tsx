import { NavLink, Outlet } from "react-router-dom";
import { CalendarDays, Compass, History, Radio } from "lucide-react";

import { cn } from "@/lib/utils";
import { useLibrary } from "@/lib/library";

const NAVIGATION = [
  { to: "/", label: "Catalog", icon: Compass, end: true },
  { to: "/airing", label: "Airing", icon: CalendarDays, end: false },
  { to: "/library", label: "Library", icon: History, end: false },
];

/** The frame of the application: the navigation on the left, the page on the right. */
export function AppShell() {
  const { status } = useLibrary();

  return (
    <div className="flex h-full w-full overflow-hidden bg-background">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-border/60 bg-sidebar/40 md:flex">
        <div className="flex items-center gap-2 px-5 py-5">
          <span className="flex size-9 items-center justify-center rounded-lg bg-primary/15 text-primary">
            <Radio className="size-5" />
          </span>
          <div className="leading-tight">
            <p className="text-sm font-semibold tracking-tight">animeplayer</p>
            <p className="text-[11px] text-muted-foreground">desktop sample</p>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-3">
          {NAVIGATION.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors",
                  isActive
                    ? "bg-primary/12 font-medium text-primary"
                    : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                )
              }
            >
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-border/60 px-5 py-4">
          <p className="flex items-center gap-2 text-[11px] text-muted-foreground">
            <span
              className={cn(
                "size-2 rounded-full",
                status.state === "ready"
                  ? "bg-emerald-500"
                  : status.state === "starting"
                    ? "animate-pulse bg-highlight"
                    : "bg-destructive",
              )}
            />
            {status.state === "ready"
              ? "The library is running"
              : status.state === "starting"
                ? "Starting the library…"
                : "The library is not running"}
          </p>
        </div>
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
