import { useEffect, useState } from 'react';

const themeKey = 'proto-studio-theme';
function savedTheme() {
  try { const value = localStorage.getItem(themeKey); return ['light', 'dark'].includes(value) ? value : null; }
  catch { return null; }
}
export default function useTheme() {
  const [preference, setPreference] = useState(savedTheme);
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches);
  const theme = preference || (systemDark ? 'dark' : 'light');
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const update = event => setSystemDark(event.matches);
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#101419' : '#f5f6f8');
  }, [theme]);
  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    setPreference(next);
    try { localStorage.setItem(themeKey, next); } catch { /* Theme still works when storage is unavailable. */ }
  }
  return [theme, toggleTheme];
}
