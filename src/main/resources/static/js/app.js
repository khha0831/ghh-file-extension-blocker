const API = '/api/extensions';

// ===== 유틸 =====
async function request(url, options = {}) {
    try {
        const res = await fetch(url, options);
        const data = await res.json();
        if (!data.success) {
            alert('⚠️ ' + data.message);
            return null;
        }
        return data;
    } catch (err) {
        alert('❌ 서버 통신 오류: ' + err.message);
        return null;
    }
}

function sanitizeInput(value) {
    return value.replace(/[^a-z0-9, ]/g, '');
}

// ===== 초기화 =====
document.addEventListener('DOMContentLoaded', () => {
    loadFixedExtensions();
    loadCustomExtensions();
    setupDragDrop();
    setupInputFilter();
    setupEnterKey();
});

// ===== 고정 확장자 =====
async function loadFixedExtensions() {
    const data = await request(`${API}/fixed`);
    if (!data) return;

    const container = document.getElementById('fixed-extensions');
    const order = ['bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js'];
    const sorted = data.data.sort((a, b) =>
        order.indexOf(a.extension) - order.indexOf(b.extension));

    container.innerHTML = sorted.map(ext => `
        <span class="checkbox-item">
            <input type="checkbox" id="fixed-${ext.extension}"
                   ${ext.blocked ? 'checked' : ''}
                   onchange="toggleFixed('${ext.extension}', this.checked)">
            <label for="fixed-${ext.extension}">${ext.extension}</label>
        </span>
    `).join('');
}

async function toggleFixed(extension, blocked) {
    const data = await request(`${API}/fixed`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ extension, blocked })
    });
    if (!data) {
        // 실패 시 체크 되돌리기
        const cb = document.getElementById('fixed-' + extension);
        if (cb) cb.checked = !blocked;
    }
}

async function bulkUpdateFixed(blocked) {
    const data = await request(`${API}/fixed/bulk?blocked=${blocked}`, { method: 'PATCH' });
    if (data) loadFixedExtensions();
}

// ===== 커스텀 확장자 =====
async function loadCustomExtensions() {
    const data = await request(`${API}/custom`);
    if (!data) return;

    const container = document.getElementById('custom-extensions');
    const countEl = document.getElementById('custom-count');
    const count = data.data.length;

    countEl.textContent = `${count}/200`;

    if (count === 0) {
        container.innerHTML = '<span class="empty-text">등록된 커스텀 확장자가 없습니다.</span>';
        return;
    }

    container.innerHTML = data.data.map(ext => `
        <span class="tag" title="${ext.createdAt}에 추가됨">
            ${ext.extension}
            <button class="delete-btn" onclick="deleteCustom(${ext.id})">&times;</button>
        </span>
    `).join('');
}

async function addCustomExtensions() {
    const input = document.getElementById('custom-input');
    const value = input.value.trim();
    if (!value) {
        alert('⚠️ 확장자를 입력해주세요.');
        input.focus();
        return;
    }

    const data = await request(`${API}/custom`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ extensions: value })
    });

    if (data) {
        input.value = '';
        loadCustomExtensions();
    }
}

async function deleteCustom(id) {
    const data = await request(`${API}/custom/${id}`, { method: 'DELETE' });
    if (data) loadCustomExtensions();
}

async function deleteAllCustom() {
    if (!confirm('커스텀 확장자를 모두 삭제하시겠습니까?')) return;
    const data = await request(`${API}/custom`, { method: 'DELETE' });
    if (data) loadCustomExtensions();
}

// ===== 초기화 =====
async function resetAll() {
    if (!confirm('모든 설정을 초기화하시겠습니까?\n(커스텀 확장자 전체 삭제 + 고정 확장자 전체 해제)')) return;
    const data = await request(`${API}/reset`, { method: 'POST' });
    if (data) {
        loadFixedExtensions();
        loadCustomExtensions();
    }
}

// ===== 테스트 데이터 =====
async function generateTestData() {
    if (!confirm('테스트 데이터(test1~test200)를 생성하시겠습니까?')) return;
    const data = await request(`${API}/test-data`, { method: 'POST' });
    if (data) {
        alert('✅ ' + data.message);
        loadCustomExtensions();
    }
}

// ===== 파일 업로드 =====
let selectedFiles = [];

function setupDragDrop() {
    const area = document.getElementById('upload-area');
    const input = document.getElementById('file-input');

    ['dragenter', 'dragover'].forEach(evt => {
        area.addEventListener(evt, e => { e.preventDefault(); area.classList.add('dragover'); });
    });
    ['dragleave', 'drop'].forEach(evt => {
        area.addEventListener(evt, e => { e.preventDefault(); area.classList.remove('dragover'); });
    });

    area.addEventListener('drop', e => {
        const newFiles = Array.from(e.dataTransfer.files);
        appendFiles(newFiles);
    });

    input.addEventListener('change', () => {
        const newFiles = Array.from(input.files);
        appendFiles(newFiles);
        input.value = '';  // 같은 파일 재선택 가능하도록 초기화
    });
}

function appendFiles(newFiles) {
    const existingNames = new Set(selectedFiles.map(f => f.name));
    for (const file of newFiles) {
        if (!existingNames.has(file.name)) {
            selectedFiles.push(file);
        }
    }
    showSelectedFiles();
}

function showSelectedFiles() {
    const container = document.getElementById('selected-files');
    const ul = document.getElementById('file-list-ul');
    const placeholder = document.getElementById('file-placeholder');

    if (selectedFiles.length === 0) {
        container.style.display = 'none';
        placeholder.textContent = '선택된 파일 없음';
        return;
    }

    placeholder.textContent = selectedFiles.length + '개 파일 선택됨';
    ul.innerHTML = selectedFiles.map((f, i) =>
        `<li>${f.name} (${formatSize(f.size)}) <button class="delete-btn" onclick="removeFile(${i})">&times;</button></li>`
    ).join('');
    container.style.display = 'block';
    document.getElementById('upload-result').style.display = 'none';
}

function clearFiles() {
    selectedFiles = [];
    document.getElementById('file-input').value = '';
    document.getElementById('selected-files').style.display = 'none';
    document.getElementById('file-placeholder').textContent = '선택된 파일 없음';
}

function removeFile(index) {
    selectedFiles.splice(index, 1);
    showSelectedFiles();
}

async function uploadFiles() {
    if (selectedFiles.length === 0) {
        alert('⚠️ 파일을 선택해주세요.');
        return;
    }

    const formData = new FormData();
    selectedFiles.forEach(f => formData.append('files', f));

    const resultDiv = document.getElementById('upload-result');

    try {
        const res = await fetch(`${API}/upload`, { method: 'POST', body: formData });
        const data = await res.json();

        resultDiv.style.display = 'block';

        if (data.success) {
            resultDiv.className = 'upload-result success';
            resultDiv.textContent = `✅ ${data.message}\n업로드 파일 (${data.data.acceptedFiles}개):\n` +
                data.data.acceptedFileNames.map(n => '  📎 ' + n).join('\n');
        } else {
            resultDiv.className = 'upload-result error';
            resultDiv.textContent = '🚫 ' + data.message;
        }
    } catch (err) {
        resultDiv.style.display = 'block';
        resultDiv.className = 'upload-result error';
        resultDiv.textContent = '❌ 업로드 실패: ' + err.message;
    }

    clearFiles();
}

// ===== 입력 필터 =====
function setupInputFilter() {
    const input = document.getElementById('custom-input');
    input.addEventListener('input', () => {
        const pos = input.selectionStart;
        const before = input.value;
        input.value = sanitizeInput(before.toLowerCase());
        const diff = before.length - input.value.length;
        input.setSelectionRange(pos - diff, pos - diff);
    });
}

function setupEnterKey() {
    document.getElementById('custom-input').addEventListener('keypress', e => {
        if (e.key === 'Enter') addCustomExtensions();
    });
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
}