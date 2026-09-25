import React from "react";
import ReactDOM from "react-dom/client";

import App from "@/App";
import { AuthProvider } from "@/hooks/use-auth";
import { I18nProvider } from "@/lib/i18n";
import "@/index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <I18nProvider><AuthProvider><App /></AuthProvider></I18nProvider>
  </React.StrictMode>,
);
