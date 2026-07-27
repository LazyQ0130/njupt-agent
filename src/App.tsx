import { Navigate, Route, Routes } from "react-router-dom";
import { AdminGuard } from "./components/AdminGuard";
import { AdminPage } from "./pages/AdminPage";
import { AdminLoginPage } from "./pages/AdminLoginPage";
import { ChatPage } from "./pages/ChatPage";
import { CrawlerAdminPage } from "./pages/CrawlerAdminPage";
import { HomePage } from "./pages/HomePage";
import { KnowledgePage } from "./pages/KnowledgePage";
import { QualityAdminPage } from "./pages/QualityAdminPage";
import { AdminSettingsPage } from "./pages/AdminSettingsPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/chat" element={<ChatPage />} />
      <Route path="/knowledge" element={<KnowledgePage />} />
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route
        path="/admin"
        element={<AdminGuard><AdminPage /></AdminGuard>}
      />
      <Route
        path="/admin/crawler"
        element={<AdminGuard><CrawlerAdminPage /></AdminGuard>}
      />
      <Route
        path="/admin/quality"
        element={<AdminGuard><QualityAdminPage /></AdminGuard>}
      />
      <Route
        path="/admin/settings"
        element={<AdminGuard><AdminSettingsPage /></AdminGuard>}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
