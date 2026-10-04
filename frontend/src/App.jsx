import { Navigate, Route, Routes } from 'react-router-dom';
import RequireAuth from './auth/RequireAuth';
import { useAuth } from './auth/AuthProvider';
import AppLayout from './layout/AppLayout';
import { MENU, homeFor } from './layout/menu';
import LoginPage from './pages/LoginPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import SetPasswordPage from './pages/SetPasswordPage';
import UsersPage from './pages/users/UsersPage';
import StockPage from './pages/stock/StockPage';
import RegistryPage from './pages/registry/RegistryPage';
import EntryPage from './pages/entry/EntryPage';
import TransferPage from './pages/transfer/TransferPage';
import SurgeryPage from './pages/surgery/SurgeryPage';
import ComingSoon from './pages/ComingSoon';

/** Screens already connected to the API; the other menu items show a placeholder until their phase. */
const PAGES = {
  '/usuarios': <UsersPage />,
  '/estoque': <StockPage />,
  '/cadastros': <RegistryPage />,
  '/entrada': <EntryPage />,
  '/transferencia': <TransferPage />,
  '/saida': <SurgeryPage />,
};

function Home() {
  const { user } = useAuth();
  return <Navigate to={homeFor(user)} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/esqueci-senha" element={<ForgotPasswordPage />} />
      <Route path="/primeiro-acesso" element={<SetPasswordPage firstAccess />} />
      <Route path="/redefinir-senha" element={<SetPasswordPage />} />

      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route index element={<Home />} />
        {MENU.map((item) => (
          <Route key={item.path} path={item.path}
            element={<RequireAuth roles={item.roles}>{PAGES[item.path] || <ComingSoon title={item.label} />}</RequireAuth>} />
        ))}
        <Route path="*" element={<Home />} />
      </Route>
    </Routes>
  );
}
