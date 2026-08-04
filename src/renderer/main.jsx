import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import { DialogProvider } from "./components/DialogProvider.jsx";
import { AchievementUnlockProvider } from "./components/AchievementUnlockCeremony.jsx";
import "./styles.css";

const root = createRoot(document.getElementById("root"));
root.render(
  <DialogProvider>
    <AchievementUnlockProvider>
      <App />
    </AchievementUnlockProvider>
  </DialogProvider>
);
