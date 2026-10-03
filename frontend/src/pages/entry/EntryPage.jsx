import { useMediaQuery } from '@mui/material';
import useEntryDraft from './useEntryDraft';
import EntryDesktop from './EntryDesktop';
import EntryMobile from './EntryMobile';

/**
 * Stock entry: material received from the supplier enters the storeroom, assigned to a hospital or to a
 * distribution center. The version follows the screen size (same breakpoint as the side menu); both share
 * the entry being registered, so turning the tablet does not lose the items already read.
 */
export default function EntryPage() {
  const isMobile = useMediaQuery('(max-width:899.95px)', { noSsr: true });
  const entry = useEntryDraft();
  return isMobile ? <EntryMobile entry={entry} /> : <EntryDesktop entry={entry} />;
}
