import { useMediaQuery } from '@mui/material';
import useTransferDraft from './useTransferDraft';
import TransferDesktop from './TransferDesktop';
import TransferMobile from './TransferMobile';

/**
 * Storeroom -> hospital transfer, recorded as a delivery (replenishment) with the Classic delivery report.
 * The version follows the screen size (same breakpoint as the side menu); both share the list being prepared.
 */
export default function TransferPage() {
  const isMobile = useMediaQuery('(max-width:899.95px)', { noSsr: true });
  const transfer = useTransferDraft();
  return isMobile ? <TransferMobile transfer={transfer} /> : <TransferDesktop transfer={transfer} />;
}
