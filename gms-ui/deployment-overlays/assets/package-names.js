(() => {
  'use strict';
  let entries;
  let pending = false;
  const style = document.createElement('style');
  style.textContent = '[data-package-key]{width:100px;max-width:100%;display:block;flex-shrink:0}.package-label{white-space:normal;overflow-wrap:anywhere}.package-remark{font-size:12px;text-align:left;white-space:normal;overflow-wrap:anywhere;margin-top:6px}.package-remark summary{cursor:pointer;color:#165dff}.package-member{margin-top:4px}';
  document.head.append(style);

  function update() {
    pending = false;
    if (!entries) return;
    for (const table of document.querySelectorAll('table')) {
      const headers = [...table.querySelectorAll('thead tr:last-child th')].map(th => th.textContent.trim());
      const snIndex = headers.indexOf('SN');
      const idIndex = headers.indexOf('物品ID');
      const nameIndex = headers.indexOf('物品名称');
      if (snIndex < 0 || idIndex < 0 || nameIndex < 0) continue;
      for (const row of table.querySelectorAll('tbody tr')) {
        const cells = [...row.children].filter(cell => cell.tagName === 'TD');
        if (!cells[nameIndex]) continue;
        const sn = cells[snIndex].textContent.trim();
        const id = cells[idIndex].textContent.trim();
        const entry = entries[sn];
        const cell = cells[nameIndex].querySelector('.arco-table-cell') || cells[nameIndex];
        const existing = cell.querySelector('[data-package-key]');
        if (!entry || String(entry.itemId) !== id) {
          if (existing) existing.remove();
          continue;
        }
        const key = `${sn}:${id}`;
        if (existing && existing.dataset.packageKey === key) continue;
        if (existing) existing.remove();
        // Keep a name supplied by the backend; only fill the missing presentation.
        if (cell.textContent.trim()) continue;
        const block = document.createElement('div');
        block.dataset.packageKey = key;
        const label = document.createElement('div');
        label.className = 'package-label';
        label.textContent = entry.name;
        block.append(label);
        if (!entry.hasName) {
          const details = document.createElement('details');
          details.className = 'package-remark';
          const summary = document.createElement('summary');
          summary.textContent = `备注：${entry.contents.length}项物品`;
          details.append(summary);
          for (const member of entry.contents) {
            const line = document.createElement('div');
            line.className = 'package-member';
            line.textContent = member.name + (member.count == null ? '' : ` ×${member.count}`);
            details.append(line);
          }
          if (!entry.contents.length) {
            const line = document.createElement('div');
            line.textContent = '数据中未找到礼包内容';
            details.append(line);
          }
          block.append(details);
        }
        cell.append(block);
      }
    }
  }
  function schedule() {
    if (!pending) { pending = true; requestAnimationFrame(update); }
  }
  new MutationObserver(schedule).observe(document.documentElement, { childList:true, subtree:true, characterData:true });
  fetch('/assets/package-catalog.json?v=20261010-zh-1', { credentials:'same-origin' })
    .then(response => { if (!response.ok) throw new Error('Package catalog unavailable'); return response.json(); })
    .then(catalog => { entries = catalog.entries; schedule(); })
    .catch(error => console.warn(error.message));
})();
