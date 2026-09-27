// Preload keeps renderer isolated. Expose a tiny native marker for the site.
const { contextBridge } = require("electron");
contextBridge.exposeInMainWorld("FilmBuffBridge", {
  platform: "desktop",
  isNative: true,
});
contextBridge.exposeInMainWorld("FilmBuffDesktop", {
  version: "1.0.0",
});
