// Configuration
const API_URL = '/api';

// État
let currentToken = localStorage.getItem('skymail_token');
let currentUser = localStorage.getItem('skymail_user');
let emails = [];
let currentReadEmailId = null;
let currentTab = 'inbox'; // inbox, sent, trash

// DOM Elements
const loginScreen = document.getElementById('login-screen');
const mainScreen = document.getElementById('main-screen');
const authForm = document.getElementById('auth-form');
const emailList = document.getElementById('email-list');
const readEmpty = document.getElementById('read-empty');
const readContent = document.getElementById('read-content');
const composeModal = document.getElementById('compose-modal');
const composeForm = document.getElementById('compose-form');
const toastEl = document.getElementById('toast');

// --- INIT ---
function init() {
    if (currentToken && currentUser) {
        showMainScreen();
        switchTab('inbox');
    } else {
        showLoginScreen();
    }
}

// --- UI NAVIGATION ---
function showLoginScreen() {
    loginScreen.classList.add('active');
    loginScreen.classList.remove('hidden');
    mainScreen.classList.add('hidden');
    mainScreen.classList.remove('active');
}

function showMainScreen() {
    document.getElementById('current-user').innerText = currentUser;
    mainScreen.classList.add('active');
    mainScreen.classList.remove('hidden');
    loginScreen.classList.add('hidden');
    loginScreen.classList.remove('active');
}

function showToast(message, isError = false) {
    toastEl.innerText = message;
    if (isError) toastEl.classList.add('error');
    else toastEl.classList.remove('error');
    
    toastEl.classList.add('show');
    setTimeout(() => toastEl.classList.remove('show'), 3000);
}

// --- AUTHENTIFICATION ---
let isLoginMode = true;

function toggleAuthMode() {
    isLoginMode = !isLoginMode;
    const subtitle = document.getElementById('form-subtitle');
    const authBtn = document.getElementById('auth-btn').querySelector('span');
    const switchText = document.getElementById('auth-switch-text');
    const switchLink = document.getElementById('auth-switch-link');

    if (isLoginMode) {
        subtitle.innerText = "Connexion à votre espace";
        authBtn.innerText = "Se connecter";
        switchText.innerText = "Pas encore de compte ?";
        switchLink.innerText = "S'inscrire";
    } else {
        subtitle.innerText = "Création d'un nouveau compte";
        authBtn.innerText = "S'inscrire";
        switchText.innerText = "Déjà un compte ?";
        switchLink.innerText = "Se connecter";
    }
}

authForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = authForm.querySelector('button');
    const btnSpan = btn.querySelector('span');
    const originalText = btnSpan.innerText;
    btnSpan.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Traitement...';
    btn.disabled = true;

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    const endpoint = isLoginMode ? '/auth/login' : '/auth/register';

    try {
        const res = await fetch(`${API_URL}${endpoint}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (res.ok) {
            if (isLoginMode) {
                const data = await res.json();
                currentToken = data.token;
                currentUser = data.username;
                localStorage.setItem('skymail_token', currentToken);
                localStorage.setItem('skymail_user', currentUser);
                showToast('Connexion réussie !');
                showMainScreen();
                switchTab('inbox');
            } else {
                showToast('Compte créé avec succès ! Connectez-vous.');
                toggleAuthMode();
            }
        } else {
            const err = await res.text();
            showToast(err, true);
        }
    } catch (e) {
        showToast('Erreur serveur: API inaccessible', true);
    } finally {
        btnSpan.innerHTML = originalText;
        btn.disabled = false;
    }
});

function logout() {
    currentToken = null;
    currentUser = null;
    localStorage.removeItem('skymail_token');
    localStorage.removeItem('skymail_user');
    
    // Fermer le message ouvert s'il y en a un
    currentReadEmailId = null;
    readContent.classList.add('hidden');
    readContent.classList.remove('active');
    readEmpty.classList.add('active');
    readEmpty.classList.remove('hidden');

    showLoginScreen();
}

// --- NAVIGATION ET DOSSIERS ---
async function switchTab(tab) {
    currentTab = tab;
    
    // Mettre à jour l'interface des boutons navigation
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    document.getElementById('nav-' + tab).classList.add('active');
    
    // Titre
    const titles = { 'inbox': 'Boîte de réception', 'sent': 'Envoyés', 'trash': 'Corbeille' };
    document.getElementById('panel-title').innerText = titles[tab];

    // Cacher le panneau de lecture au changement de dossier
    currentReadEmailId = null;
    readContent.classList.add('hidden');
    readContent.classList.remove('active');
    readEmpty.classList.add('active');
    readEmpty.classList.remove('hidden');

    if (tab === 'inbox') {
        await fetchMessages('/messages');
    } else if (tab === 'sent') {
        await fetchMessages('/messages/sent');
    } else if (tab === 'trash') {
        emails = [];
        renderInbox('Votre corbeille est vide.');
    }
}

function refreshCurrentTab() {
    switchTab(currentTab);
}

// --- MESSAGERIE ---
async function fetchMessages(endpoint) {
    try {
        const res = await fetch(`${API_URL}${endpoint}`, {
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });

        if (res.status === 401) { logout(); return; }
        
        emails = await res.json();
        
        if (currentTab === 'inbox') {
            const unreadCount = emails.filter(e => !(e.read || e.isRead)).length;
            document.getElementById('unread-badge').innerText = unreadCount;
        }

        
        renderInbox();
    } catch (e) {
        showToast('Erreur lors du chargement des messages', true);
    }
}

function renderInbox(emptyMessage = 'Aucun message.') {
    if (emails.length === 0) {
        emailList.innerHTML = `<div class="empty-state">
            <i class="fa-solid fa-envelope-open-text"></i>
            <p>${currentTab === 'inbox' ? 'Votre boîte de réception est vide.' : emptyMessage}</p>
        </div>`;
        return;
    }

    emailList.innerHTML = emails.map((email, idx) => {
        const isRead = email.read || email.isRead;
        const emailDate = email.date || email.createdAt;
        return `
        <div class="email-item ${currentReadEmailId === email.id ? 'active' : ''} ${!isRead ? 'unread' : ''}" onclick="readEmail(${idx})">
            <div class="email-sender">
                <span>
                    ${!isRead ? '<span class="unread-dot"></span>' : ''}${email.sender}
                </span>
                <span class="email-time">${formatDate(emailDate)}</span>
            </div>
            <div class="email-subject">${email.subject || 'Sans sujet'}</div>
            <div class="email-preview">${email.content ? email.content.substring(0, 50) + '...' : ''}</div>
        </div>
        `;
    }).join('');
}

function readEmail(idx) {
    const email = emails[idx];
    currentReadEmailId = email.id;
    
    // Mark as read if it's not
    const isRead = email.read || email.isRead;
    if (!isRead) {
        email.read = true; // updates local state
        email.isRead = true;
        
        fetch(`${API_URL}/messages/${email.id}/read`, {
             method: 'PUT',
             headers: { 'Authorization': `Bearer ${currentToken}` }
        }).catch(err => console.error("Error marking as read", err));
        
        if (currentTab === 'inbox') {
             const unreadCount = emails.filter(e => !(e.read || e.isRead)).length;
             document.getElementById('unread-badge').innerText = unreadCount;
        }
    }
    
    // Rerender list for active state
    renderInbox();

    // Show Read Panel
    readEmpty.classList.remove('active');
    readEmpty.classList.add('hidden');
    readContent.classList.remove('hidden');
    readContent.classList.add('active');

    const emailDate = email.date || email.createdAt;
    
    // Populate Read Panel
    document.getElementById('read-avatar').innerText = email.sender.charAt(0).toUpperCase();
    document.getElementById('read-subject').innerText = email.subject || 'Sans sujet';
    document.getElementById('read-sender').innerText = email.sender;
    document.getElementById('read-date').innerText = formatDateFull(emailDate);
    document.getElementById('read-body').innerText = email.content;

    // Attach delete action
    document.getElementById('delete-btn').onclick = () => deleteEmail(email.id);
}

async function deleteEmail(id) {
    if (!confirm('Supprimer ce message définitivement ?')) return;

    try {
        const res = await fetch(`${API_URL}/messages/${id}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });

        if (res.ok) {
            showToast('Message supprimé');
            // Hide read panel if it was the deleted message
            if (currentReadEmailId === id) {
                readContent.classList.add('hidden');
                readContent.classList.remove('active');
                readEmpty.classList.add('active');
                readEmpty.classList.remove('hidden');
                currentReadEmailId = null;
            }
            refreshCurrentTab(); // Refresh list
        } else {
            showToast('Impossible de supprimer', true);
        }
    } catch (e) {
        showToast('Erreur serveur', true);
    }
}

// --- NOUVEAU MESSAGE (COMPOSER) ---
function openComposeModal() {
    composeModal.classList.remove('hidden');
    composeModal.classList.add('active');
    document.getElementById('compose-to').focus();
}

function closeComposeModal() {
    composeModal.classList.remove('active');
    composeModal.classList.add('hidden');
    composeForm.reset();
}

composeForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = composeForm.querySelector('button[type="submit"]');
    const originalText = btn.innerHTML;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Envoi...';
    btn.disabled = true;

    const payload = {
        to: document.getElementById('compose-to').value,
        subject: document.getElementById('compose-subject').value,
        content: document.getElementById('compose-body').value
    };

    try {
        const res = await fetch(`${API_URL}/messages/send`, {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}` 
            },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast('Message envoyé !');
            closeComposeModal();
        } else {
            const err = await res.text();
            showToast(err, true);
        }
    } catch (e) {
        showToast('Erreur lors de l\'envoi', true);
    } finally {
        btn.innerHTML = originalText;
        btn.disabled = false;
    }
});

// --- UTILS ---
function formatDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDateFull(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleString();
}

// Démarrage initial
init();
