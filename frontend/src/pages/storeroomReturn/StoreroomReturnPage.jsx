import { useMediaQuery } from '@mui/material';
import useStoreroomReturnDraft from './useStoreroomReturnDraft';
import StoreroomReturnDesktop from './StoreroomReturnDesktop';
import StoreroomReturnMobile from './StoreroomReturnMobile';

/**
 * Hospital -> storeroom withdrawal, with its own Classic report. The version follows the screen size
 * (same breakpoint as the side menu); both share the list being prepared.
 */
export default function StoreroomReturnPage() {
  const isMobile = useMediaQuery('(max-width:899.95px)', { noSsr: true });
  const draft = useStoreroomReturnDraft();
  return isMobile ? <StoreroomReturnMobile draft={draft} /> : <StoreroomReturnDesktop draft={draft} />;
}
