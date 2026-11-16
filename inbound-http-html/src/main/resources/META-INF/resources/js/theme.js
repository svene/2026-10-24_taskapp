// Dark/light theme toggle. Loaded synchronously in <head>, so a saved choice is applied before
// the first paint. Without a saved choice Bulma follows the OS setting (prefers-color-scheme);
// a saved choice overrides it via <html data-theme="dark|light">, which Bulma 1.x reads.
(() => {
	const root = document.documentElement;
	try {
		const saved = localStorage.getItem('theme');
		if (saved === 'dark' || saved === 'light') root.dataset.theme = saved;
	} catch (e) { /* storage blocked: follow the OS setting */ }

	document.addEventListener('click', (event) => {
		if (!event.target.closest('.theme-toggle')) return;
		const current = root.dataset.theme
			?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
		const next = current === 'dark' ? 'light' : 'dark';
		root.dataset.theme = next;
		try { localStorage.setItem('theme', next); } catch (e) { /* not remembered */ }
	});
})();
