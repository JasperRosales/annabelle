// Assembles the page: every <div data-include="views/x.html"> is replaced with that file's HTML.
// The desktop shell serves these files from localhost (StaticUiServer), so they load instantly.
// app.js waits for TALKCARE_VIEWS_READY before it touches the DOM.
window.TALKCARE_VIEWS_READY = (async () => {
  const slots = Array.from(document.querySelectorAll("[data-include]"));
  const html = await Promise.all(slots.map(async (slot) => {
    const res = await fetch(slot.dataset.include, { cache: "no-store" });
    if (!res.ok) throw new Error(`Could not load ${slot.dataset.include} (HTTP ${res.status})`);
    return res.text();
  }));
  slots.forEach((slot, i) => {
    const template = document.createElement("template");
    template.innerHTML = html[i];
    slot.replaceWith(template.content);
  });
})();
