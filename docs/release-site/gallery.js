"use strict";

const tablist = document.querySelector(".tabs");

if (tablist) {
  const tabs = Array.from(tablist.querySelectorAll('[role="tab"]'));
  const panels = tabs.map((tab) => document.getElementById(tab.getAttribute("aria-controls")));

  function selectTab(index, moveFocus = false) {
    tabs.forEach((tab, i) => {
      const selected = i === index;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
      panels[i].hidden = !selected;
      panels[i].tabIndex = selected ? 0 : -1;
    });

    if (moveFocus) tabs[index].focus();
  }

  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => selectTab(i, true));
    tab.addEventListener("keydown", (event) => {
      let next;
      switch (event.key) {
        case "ArrowLeft": next = (i - 1 + tabs.length) % tabs.length; break;
        case "ArrowRight": next = (i + 1) % tabs.length; break;
        case "Home": next = 0; break;
        case "End": next = tabs.length - 1; break;
        default: return;
      }

      event.preventDefault();
      selectTab(next, true);
    });
  });

  selectTab(0);
  tablist.hidden = false;
}
