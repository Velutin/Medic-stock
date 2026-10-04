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
  return isMobile ? <SurgeryMobile draft={draft} /> : <SurgeryDesktop draft={draft} />;
}
