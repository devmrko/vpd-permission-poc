document.body.addEventListener('htmx:responseError', (event) => {
  const target = event.detail.target;
  if (target) {
    target.innerHTML = '<div class="alert alert-danger">요청 처리 중 오류가 발생했습니다.</div>';
  }
});

function escapeHtml(value) {
  return value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
}

function renderInlineMarkdown(value) {
  return escapeHtml(value)
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');
}

function isMarkdownTable(lines, index) {
  if (index + 1 >= lines.length) {
    return false;
  }
  return lines[index].trim().startsWith('|')
      && lines[index + 1].trim().startsWith('|')
      && /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(lines[index + 1].trim());
}

function splitMarkdownTableRow(line) {
  return line.trim()
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((cell) => cell.trim());
}

function renderMarkdownTable(lines, start) {
  const headers = splitMarkdownTableRow(lines[start]);
  let cursor = start + 2;
  const rows = [];
  while (cursor < lines.length && lines[cursor].trim().startsWith('|')) {
    rows.push(splitMarkdownTableRow(lines[cursor]));
    cursor += 1;
  }
  const headerHtml = headers.map((cell) => `<th>${renderInlineMarkdown(cell)}</th>`).join('');
  const bodyHtml = rows.map((row) => {
    const cells = headers.map((_, index) => `<td>${renderInlineMarkdown(row[index] || '')}</td>`).join('');
    return `<tr>${cells}</tr>`;
  }).join('');
  return {
    html: `<table><thead><tr>${headerHtml}</tr></thead><tbody>${bodyHtml}</tbody></table>`,
    next: cursor
  };
}

function renderMarkdownText(markdown) {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const html = [];
  let listType = null;
  const closeList = () => {
    if (listType) {
      html.push(`</${listType}>`);
      listType = null;
    }
  };
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const trimmed = line.trim();
    if (!trimmed) {
      closeList();
      continue;
    }
    if (isMarkdownTable(lines, i)) {
      closeList();
      const table = renderMarkdownTable(lines, i);
      html.push(table.html);
      i = table.next - 1;
      continue;
    }
    const heading = trimmed.match(/^(#{2,4})\s+(.+)$/);
    if (heading) {
      closeList();
      const level = Math.min(heading[1].length, 3);
      html.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
      continue;
    }
    const unordered = trimmed.match(/^[-*]\s+(.+)$/);
    if (unordered) {
      if (listType !== 'ul') {
        closeList();
        listType = 'ul';
        html.push('<ul>');
      }
      html.push(`<li>${renderInlineMarkdown(unordered[1])}</li>`);
      continue;
    }
    const ordered = trimmed.match(/^\d+\.\s+(.+)$/);
    if (ordered) {
      if (listType !== 'ol') {
        closeList();
        listType = 'ol';
        html.push('<ol>');
      }
      html.push(`<li>${renderInlineMarkdown(ordered[1])}</li>`);
      continue;
    }
    closeList();
    html.push(`<p>${renderInlineMarkdown(trimmed)}</p>`);
  }
  closeList();
  return html.join('');
}

function renderMarkdownViews(root = document) {
  root.querySelectorAll('[data-markdown-view]').forEach((view) => {
    if (view.dataset.rendered === 'true') {
      return;
    }
    view.innerHTML = renderMarkdownText(view.textContent || '');
    view.dataset.rendered = 'true';
  });
}

function filterUserRoleDetail() {
  const master = document.getElementById('userRoleMaster');
  const table = document.getElementById('userRoleDetail');
  if (!master || !table) {
    return;
  }
  const selected = master.value;
  let shown = 0;
  table.querySelectorAll('tbody tr[data-user-id]').forEach((row) => {
    const visible = row.dataset.userId === selected;
    row.hidden = !visible;
    shown += visible ? 1 : 0;
  });
  let empty = table.querySelector('tbody tr.empty-user-role-runtime');
  if (!empty) {
    empty = document.createElement('tr');
    empty.className = 'empty-user-role-runtime';
    empty.innerHTML = '<td colspan="3" class="text-muted">선택한 사용자에게 부여된 역할이 없습니다.</td>';
    table.querySelector('tbody').appendChild(empty);
  }
  empty.hidden = shown !== 0;
}

function renderRuleColumnOptions(columns) {
  document.querySelectorAll('.rule-column-select').forEach((select) => {
    const current = select.value;
    select.innerHTML = '<option value="">기본 컬럼</option>' + columns
        .map((column) => `<option value="${column}">${column}</option>`)
        .join('');
    if (columns.includes(current)) {
      select.value = current;
    }
  });
}

function syncRuleTypeHints(root = document) {
  root.querySelectorAll('.rule-row').forEach((row) => {
    const typeSelect = row.querySelector('.rule-type-select');
    const columnSelect = row.querySelector('.rule-column-select');
    const valueInput = row.querySelector('input[name="ruleValue"]');
    if (!typeSelect || !columnSelect || !valueInput) {
      return;
    }

    const type = typeSelect.value;
    const valueRequired = ['=', '!=', 'DEPT', 'EMP_NO'].includes(type);
    const columnOptional = ['ALL', 'MY_DEPT', 'SELF', 'DEPT', 'EMP_NO'].includes(type);
    const placeholderByType = {
      ALL: '값 불필요',
      MY_DEPT: '값 불필요',
      SELF: '값 불필요',
      DEPT: '예: HR',
      EMP_NO: '예: E2001',
      '=': '비교 값',
      '!=': '비교 값'
    };

    columnSelect.required = !columnOptional;
    valueInput.required = valueRequired;
    valueInput.disabled = ['ALL', 'MY_DEPT', 'SELF'].includes(type);
    valueInput.placeholder = placeholderByType[type] || '비교 값';
    if (valueInput.disabled) {
      valueInput.value = '';
    }
  });
}

async function syncRuleColumnOptions() {
  const objectSelect = document.querySelector('select[name="objectRef"]');
  if (!objectSelect) {
    return;
  }
  const selectedOption = objectSelect.options[objectSelect.selectedIndex];
  const fallbackColumns = (selectedOption?.dataset.columns || '').split(',').filter(Boolean);
  renderRuleColumnOptions(fallbackColumns);
  try {
    const response = await fetch(`/permissions/object-columns?objectRef=${encodeURIComponent(objectSelect.value)}`);
    if (!response.ok) {
      return;
    }
    const columns = await response.json();
    renderRuleColumnOptions(Array.isArray(columns) ? columns : fallbackColumns);
  } catch (error) {
    renderRuleColumnOptions(fallbackColumns);
  }
}

async function syncObjectCatalogSelection() {
  const catalog = document.getElementById('objectCatalogSelect');
  if (!catalog || !catalog.value) {
    return;
  }
  const selected = catalog.options[catalog.selectedIndex];
  const ownerInput = document.querySelector('input[name="owner"]');
  const objectInput = document.querySelector('input[name="objectName"]');
  const ordsPathInput = document.querySelector('input[name="ordsPath"]');
  if (ownerInput) {
    ownerInput.value = selected.dataset.owner || '';
  }
  if (objectInput) {
    objectInput.value = selected.dataset.objectName || '';
  }
  if (ordsPathInput) {
    const owner = (selected.dataset.owner || '').toLowerCase();
    const objectName = (selected.dataset.objectName || '').toLowerCase();
    ordsPathInput.value = owner && objectName ? `cb-ords/cb-object-query/${owner}/${objectName}` : '';
  }
}

document.addEventListener('DOMContentLoaded', () => {
  renderMarkdownViews();
  const master = document.getElementById('userRoleMaster');
  if (master) {
    master.addEventListener('change', filterUserRoleDetail);
    filterUserRoleDetail();
  }
  const objectSelect = document.querySelector('select[name="objectRef"]');
  if (objectSelect) {
    objectSelect.addEventListener('change', syncRuleColumnOptions);
    syncRuleColumnOptions();
  }
  document.querySelectorAll('.rule-type-select').forEach((select) => {
    select.addEventListener('change', () => syncRuleTypeHints());
  });
  syncRuleTypeHints();
  const catalog = document.getElementById('objectCatalogSelect');
  if (catalog) {
    catalog.addEventListener('change', syncObjectCatalogSelection);
  }
  document.querySelectorAll('[data-rule-add]').forEach((button) => {
    button.addEventListener('click', () => {
      const row = button.closest('.rule-row');
      const list = document.getElementById('rowRuleList');
      if (!row || !list) {
        return;
      }
      const clone = row.cloneNode(true);
      clone.querySelectorAll('input').forEach((input) => input.value = '');
      const cloneButton = clone.querySelector('[data-rule-add]');
      cloneButton.textContent = '삭제';
      cloneButton.addEventListener('click', () => clone.remove());
      clone.querySelectorAll('.rule-type-select').forEach((select) => {
        select.addEventListener('change', () => syncRuleTypeHints());
      });
      list.appendChild(clone);
      syncRuleColumnOptions();
      syncRuleTypeHints(clone);
    });
  });
});

document.body.addEventListener('htmx:afterSwap', (event) => {
  renderMarkdownViews(event.detail.target || document);
});
