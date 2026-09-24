/** Referrer-restricted Maps JavaScript key (design v1.1.1 §11); absent in dev unless set in .env.local. */
export const MAPS_KEY = (import.meta.env.VITE_MAPS_API_KEY as string | undefined) || undefined
