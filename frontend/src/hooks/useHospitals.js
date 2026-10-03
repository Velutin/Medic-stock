import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import { useNotify } from '../notifications/NotificationProvider';

/**
 * Hospitals visible to the user. includeInactive (administrators): also inactive ones, for the registry.
 * Returns { hospitals, loading, reload }.
 */
export default function useHospitals({ includeInactive = false } = {}) {
  const notify = useNotify();
  const [hospitals, setHospitals] = useState([]);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setHospitals(await api('/hospitals', { query: includeInactive ? { includeInactive: true } : undefined }));
    } catch (err) {
      notify.error(err);
    } finally {
      setLoading(false);
    }
  }, [includeInactive, notify]);

  useEffect(() => { reload(); }, [reload]);
  return { hospitals, loading, reload };
}
