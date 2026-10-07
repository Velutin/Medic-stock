import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMediaQuery } from '@mui/material';
import useSurgeryDraft from './useSurgeryDraft';
import SurgeryDesktop from './SurgeryDesktop';
import SurgeryMobile from './SurgeryMobile';

/**
 * Surgery withdrawal: items read one by one (camera, reader or typed) or all at once from the consumption sheet
 * (PDF or photos), reviewed before recording. The version follows the screen size; both share the same surgery.
 */
export default function SurgeryPage() {
  const isMobile = useMediaQuery('(max-width:899.95px)', { noSsr: true });
  const draft = useSurgeryDraft();
  // Coming from the reports ("Editar itens"): opens that surgery and clears the state so a reload does not reopen it
  const location = useLocation();
  const navigate = useNavigate();
  const surgeryId = location.state?.surgeryId;
  useEffect(() => {
    if (!surgeryId) return;
    draft.resume(surgeryId);
    navigate(location.pathname, { replace: true, state: null });
  }, [surgeryId]); // runs when a surgery is handed over
  return isMobile ? <SurgeryMobile draft={draft} /> : <SurgeryDesktop draft={draft} />;
}
