document.body.addEventListener('htmx:responseError', (event) => {
  const target = event.detail.target;
  if (target) {
    target.innerHTML = '<div class="alert alert-danger">요청 처리 중 오류가 발생했습니다.</div>';
  }
});

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
    select.innerHTML = '<option value="">전체 행</option>' + columns
        .map((column) => `<option value="${column}">${column}</option>`)
        .join('');
    if (columns.includes(current)) {
      select.value = current;
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
  const columnsInput = document.querySelector('input[name="columns"]');
  if (ownerInput) {
    ownerInput.value = selected.dataset.owner || '';
  }
  if (objectInput) {
    objectInput.value = selected.dataset.objectName || '';
  }
  if (ordsPathInput) {
    ordsPathInput.value = selected.dataset.defaultPath || '';
  }
  if (!columnsInput) {
    return;
  }
  columnsInput.value = '';
  try {
    const response = await fetch(`/permissions/object-columns?objectRef=${encodeURIComponent(`db:${catalog.value}`)}`);
    if (!response.ok) {
      return;
    }
    const columns = await response.json();
    if (Array.isArray(columns)) {
      columnsInput.value = columns.join(',');
    }
  } catch (error) {
    columnsInput.value = '';
  }
}

document.addEventListener('DOMContentLoaded', () => {
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
      list.appendChild(clone);
      syncRuleColumnOptions();
    });
  });
});
