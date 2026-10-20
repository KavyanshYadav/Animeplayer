import { useCallback, useEffect, useState } from "react";

/**
 * The favourites of the user.
 *
 * A small list of AniList ids, remembered in the browser. The library has no notion
 * of a list, so this belongs to the frontend.
 */

const KEY = "animeplayer.favourites";

function read(): number[] {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((value) => typeof value === "number") : [];
  } catch {
    return [];
  }
}

function write(ids: number[]) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(ids));
  } catch {
    /* the favourites are not remembered */
  }
  window.dispatchEvent(new CustomEvent("animeplayer:favourites"));
}

/** The ids of the favourites. */
export function useFavourites(): {
  ids: number[];
  isFavourite: (id: number) => boolean;
  toggle: (id: number) => void;
} {
  const [ids, setIds] = useState<number[]>(() => read());

  useEffect(() => {
    const update = () => setIds(read());
    window.addEventListener("animeplayer:favourites", update);
    window.addEventListener("storage", update);
    return () => {
      window.removeEventListener("animeplayer:favourites", update);
      window.removeEventListener("storage", update);
    };
  }, []);

  const toggle = useCallback((id: number) => {
    const current = read();
    write(current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  }, []);

  return {
    ids,
    isFavourite: (id: number) => ids.includes(id),
    toggle,
  };
}
