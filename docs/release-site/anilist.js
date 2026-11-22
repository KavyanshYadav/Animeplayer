// SPDX-License-Identifier: GPL-3.0-or-later
const fragment = new URLSearchParams(location.hash.slice(1));
const expected = sessionStorage.getItem("anilist-sign-in-state");
const token = expected && fragment.get("state") === expected ? fragment.get("access_token") : null;
const form = document.querySelector("#login");
if (location.hash) {
  history.replaceState(null, "", location.pathname);
  sessionStorage.removeItem("anilist-sign-in-state");
}
if (token) {
  form.hidden = true;
  document.querySelector("#result").hidden = false;
  document.querySelector("#token").value = token;
}

form.addEventListener("submit", event => {
  event.preventDefault();
  const url = new URL("https://anilist.co/api/v2/oauth/authorize");
  url.searchParams.set("client_id", document.querySelector("#client").value);
  url.searchParams.set("response_type", "token");
  const state = crypto.randomUUID();
  sessionStorage.setItem("anilist-sign-in-state", state);
  url.searchParams.set("state", state);
  location.href = url;
});

document.querySelector("#copy").addEventListener("click", async () => {
  const status = document.querySelector("#status");
  try {
    await navigator.clipboard.writeText(token);
    status.textContent = "Copied.";
  } catch {
    document.querySelector("#token").select();
    status.textContent = "Use your browser’s Copy action.";
  }
});
