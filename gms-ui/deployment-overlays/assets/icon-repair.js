(() => {
  'use strict';
  const base = 'https://maplestory.io/api';
  const attempts = new WeakMap();
  const original = new WeakMap();
  const remembered = new Map();
  function describe(src) {
    try {
      const url = new URL(src, location.href);
      if (url.origin !== 'https://maplestory.io') return null;
      const match = url.pathname.match(/^\/api\/([^/]+)\/([^/]+)\/(item|mob|npc|skill)\/(\d+)\/icon(?:Raw)?$/i);
      if (!match) return null;
      return { region: match[1], version: match[2], kind: match[3].toLowerCase(), id: Number(match[4]) };
    } catch { return null; }
  }
  function rawPaths(info) {
    const { kind, id } = info;
    const padded = String(id).padStart(8, '0');
    if (kind === 'skill') return [`Skill/${Math.floor(id / 10000)}.img/skill/${id}/icon`];
    if (kind === 'mob' || kind === 'npc') {
      const folder = kind === 'mob' ? 'Mob' : 'Npc';
      return [`${folder}/${padded}.img/stand/0`, `${folder}/${padded}.img/fly/0`];
    }
    const group = Math.floor(id / 1000000);
    if (group === 1) {
      const type = Math.floor(id / 10000);
      const folder = ({100:'Cap',101:'Accessory',102:'Accessory',103:'Accessory',104:'Coat',105:'Longcoat',106:'Pants',107:'Shoes',108:'Glove',109:'Shield',110:'Cape',111:'Ring',112:'Accessory',113:'Accessory',114:'Accessory'})[type] || (type >= 130 && type <= 170 ? 'Weapon' : 'Accessory');
      return [`Character/${folder}/${padded}.img/info/icon`];
    }
    if (group === 5 && id < 5010000) return [`Item/Pet/${padded.slice(1)}.img/info/icon`];
    const folder = ({2:'Consume',3:'Install',4:'Etc',5:'Cash',9:'Special'})[group];
    if (!folder) return [];
    const image = String(Math.floor(id / 10000)).padStart(4, '0');
    const prefix = `Item/${folder}/${image}.img`;
    return group === 9 ? [`${prefix}/${id}/icon`] : [`${prefix}/${padded}/info/icon`, `${prefix}/${id}/info/icon`];
  }
  function candidates(info) {
    return rawPaths(info).map(path => `${base}/wz/img/${info.region}/${info.version}/${path}`);
  }
  function repair(img) {
    if (!(img instanceof HTMLImageElement)) return;
    const src = img.getAttribute('src') || '';
    const info = describe(src);
    if (!info) return;
    if (original.get(img) !== src) {
      delete img.dataset.iconUnavailable;
      original.set(img, src);
      attempts.set(img, new Set([src]));
    }
    const known = remembered.get(src);
    const next = known || (info.kind === 'item' && Math.floor(info.id / 1000000) === 9 ? candidates(info)[0] : null);
    if (next && next !== src) {
      attempts.get(img).add(next);
      img.src = next;
    }
  }
  document.addEventListener('error', event => {
    const img = event.target;
    if (!(img instanceof HTMLImageElement)) return;
    const source = original.get(img) || img.getAttribute('src');
    const info = describe(source);
    if (!info) return;
    const tried = attempts.get(img) || new Set();
    tried.add(img.src);
    attempts.set(img, tried);
    const next = candidates(info).find(url => !tried.has(url));
    // Keep component-level error handlers from hiding an image we can retry.
    event.stopImmediatePropagation();
    if (next) {
      tried.add(next);
      img.src = next;
    } else {
      img.src = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect x="1" y="1" width="30" height="30" rx="3" fill="#f2f3f5" stroke="#c9cdd4"/><text x="16" y="22" text-anchor="middle" font-family="Arial" font-size="20" fill="#86909c">?</text></svg>');
      img.style.width = '32px';
      img.style.height = '32px';
      img.style.objectFit = 'contain';
      img.dataset.iconUnavailable = 'true';
    }
  }, true);
  document.addEventListener('load', event => {
    const img = event.target;
    if (img instanceof HTMLImageElement && original.has(img) && !img.dataset.iconUnavailable) {
      remembered.set(original.get(img), img.src);
    }
  }, true);
  const observer = new MutationObserver(records => {
    for (const record of records) {
      if (record.type === 'attributes') repair(record.target);
      for (const node of record.addedNodes) {
        if (node instanceof Element) {
          repair(node);
          node.querySelectorAll('img').forEach(repair);
        }
      }
    }
  });
  observer.observe(document.documentElement, { subtree: true, childList: true, attributes: true, attributeFilter: ['src'] });
  document.querySelectorAll('img').forEach(repair);
})();
