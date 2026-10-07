import { useMediaQuery } from '@mui/material';
import useLoanDraft from './useLoanDraft';
import LoanDesktop from './LoanDesktop';
import LoanMobile from './LoanMobile';

/**
 * Loans between hospitals and returns to Baumer. The version follows the screen size (same breakpoint as the
 * side menu); both share the list being prepared and show the history of loans and returns.
 */
export default function LoanPage() {
  const isMobile = useMediaQuery('(max-width:899.95px)', { noSsr: true });
  const loan = useLoanDraft();
  return isMobile ? <LoanMobile loan={loan} /> : <LoanDesktop loan={loan} />;
}
