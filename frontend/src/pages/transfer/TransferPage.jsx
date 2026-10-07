import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
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
  // Coming from the Replenishment screen: destination and quantities already filled
  const location = useLocation();
  const navigate = useNavigate();
  const [prefill] = useState(() => location.state?.prefill || null);
  useEffect(() => {
    if (location.state?.prefill) navigate(location.pathname, { replace: true, state: null });
  }, []); // only once: clears the state so a reload does not fill it again
  const transfer = useTransferDraft(prefill);
  return isMobile ? <TransferMobile transfer={transfer} /> : <TransferDesktop transfer={transfer} />;
}
