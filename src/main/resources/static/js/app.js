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

document.addEventListener('DOMContentLoaded', () => {
  const master = document.getElementById('userRoleMaster');
  if (master) {
    master.addEventListener('change', filterUserRoleDetail);
    filterUserRoleDetail();
  }
});
