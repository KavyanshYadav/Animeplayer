import { NavLink, Outlet } from "react-router-dom";
import {
  Bookmark,
  CalendarDays,
  Compass,
  Heart,
  PanelLeftClose,
  Search,
  Settings,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { useLibrary } from "@/lib/library";
import { useState } from "react";
import { SettingsSheet } from "@/components/SettingsSheet";

const NAVIGATION = [
  { to: "/", label: "Home", icon: Compass, end: true },
  { to: "/search", label: "Search", icon: Search, end: false },
  { to: "/airing", label: "Airing", icon: CalendarDays, end: false },
  { to: "/library", label: "Library", icon: Bookmark, end: false },
  { to: "/favourites", label: "Favourites", icon: Heart, end: false },
];

/**
 * The frame of the application: a narrow rail of icons on the left and the page on
 * the right.
 */
export function AppShell() {
  const { status } = useLibrary();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="flex h-full w-full overflow-hidden bg-background">
      <aside
        className={cn(
          "flex shrink-0 flex-col items-center gap-1 border-r border-border/50 bg-background py-3 transition-[width] duration-200",
          collapsed ? "w-14" : "w-16",
        )}
      >
        <button
          type="button"
          onClick={() => setCollapsed((value) => !value)}
          title={collapsed ? "Expand the rail" : "Collapse the rail"}
          className="mb-2 flex size-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground"
        >
          <PanelLeftClose
            className={cn("size-5 transition-transform", collapsed && "rotate-180")}
          />
        </button>

        <nav className="flex flex-1 flex-col items-center gap-1">
          {NAVIGATION.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              title={item.label}
              className={({ isActive }) =>
                cn(
                  "flex size-9 items-center justify-center rounded-lg transition-colors",
                  isActive
                    ? "bg-foreground text-background"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground",
                )
              }
            >
              <item.icon className="size-[1.15rem]" />
            </NavLink>
          ))}
        </nav>

        <div className="flex flex-col items-center gap-1">
          <span
            title={status.state === "ready" ? "The library is running" : "The library is not running"}
            className={cn(
              "mb-1 size-2 rounded-full",
              status.state === "ready"
                ? "bg-emerald-500"
                : status.state === "starting"
                  ? "animate-pulse bg-primary"
                  : "bg-destructive",
            )}
          />
          <button
            type="button"
            onClick={() => setSettingsOpen(true)}
            title="The settings"
            className="flex size-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            <Settings className="size-[1.15rem]" />
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">
        <Outlet />
      </main>

      <SettingsSheet open={settingsOpen} onOpenChange={setSettingsOpen} />
    </div>
  );
}
