/**
 * TalkCare Desktop Application JavaScript Engine
 */

// Application State
let currentUser = null; // No user logged in by default — must log in first
let currentSession = null;

let currentService = "JUSTICE_CARE"; // 'JUSTICE_CARE' or 'THOMATT'
let currentConversationId = null;
let professionalConversationId = null;
let professionalConversations = [];
let messageRefreshTimer = null;
let pendingChatAttachment = null;
let currentReportsFilter = "All";
let reportsCache = [];
const chatServices = {
  JUSTICE_CARE: {
    name: "JusticeCare",
    professionalName: "Atty. Sofia Ramos",
    professionalTitle: "Licensed Case Handler & Legal Rights Advocate",
    seal: "assets/seal-justicecare.svg",
    avatar: "assets/avatar-case-handler.svg",
    preview: "JusticeCare Legal Support",
    placeholder: "Type your concerns....."
  },
  THOMATT: {
    name: "ThoMatt",
    professionalName: "Dr. Eleanor Reyes",
    professionalTitle: "R.Psy / L.P.T. (Licensed Psychologist & Therapist)",
    seal: "assets/seal-thomatt.svg",
    avatar: "assets/avatar-therapist.svg",
    preview: "ThoMatt Consultation",
    placeholder: "Tell your thoughts ..."
  }
};

// Init on Load
document.addEventListener("DOMContentLoaded", () => {
  initApp();
});

async function initApp() {
  renderCalendar();
  await checkDatabaseStatus();
  // Always start at welcome page — no auto-login
  currentUser = null;
  currentSession = null;
  updateAuthUI();
  const resetToken = new URLSearchParams(window.location.search).get("resetToken");
  if (resetToken) {
    document.getElementById("password-reset-token").value = resetToken;
    navigateTo("reset-password");
  } else {
    navigateTo("welcome");
  }
}

// NAVIGATION SYSTEM
function navigateTo(viewId) {
  if (messageRefreshTimer !== null) {
    clearInterval(messageRefreshTimer);
    messageRefreshTimer = null;
  }

  if (currentSession) {
    const roleHome = getRoleHomeView(currentSession.role);
    if (viewId === "home") viewId = roleHome;
    if (viewId === "dashboard" && currentSession.role !== "USER") viewId = roleHome;
    if (viewId === "admin" && currentSession.role !== "ADMIN") viewId = roleHome;
  }

  // Hide all screens
  document.querySelectorAll(".view-screen").forEach(el => el.classList.remove("active"));

  // Hide dropdown menu if open
  closeUserDropdown();

  // Highlight active navbar link
  document.querySelectorAll(".nav-links a").forEach(a => a.classList.remove("active"));
  const navItem = document.getElementById("nav-item-" + viewId);
  if (navItem) navItem.classList.add("active");

  const target = document.getElementById("view-" + viewId);
  if (target) {
    target.classList.add("active");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  // Toggle dark-navbar class for welcome page gradient
  if (viewId === "welcome") {
    document.body.classList.add("page-welcome");
  } else {
    document.body.classList.remove("page-welcome");
  }

  // Refresh view-specific content
  if (viewId === "dashboard") {
    loadReports();
  } else if (viewId === "admin") {
    loadAdminDashboard();
  } else if (viewId === "professional-dashboard" || viewId === "case-handler-dashboard") {
    if (currentSession) {
      document.querySelectorAll(".role-dashboard-user-name").forEach(name => {
        name.textContent = currentSession.name;
      });
    }
    loadProfessionalInbox();
    messageRefreshTimer = setInterval(loadProfessionalInbox, 4000);
  } else if (viewId === "chat") {
    loadConversationMessages();
    messageRefreshTimer = setInterval(() => loadConversationMessages(true), 4000);
  } else if (viewId === "profile") {
    populateProfileFields();
  }
}

function getRoleHomeView(role) {
  if (role === "ADMIN") return "admin";
  if (role === "THERAPIST") return "professional-dashboard";
  if (role === "CASE_HANDLER") return "case-handler-dashboard";
  return "home";
}

function apiFetch(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (currentSession && currentSession.token) {
    headers["X-Session-Token"] = currentSession.token;
  }
  return fetch(url, { ...options, headers });
}

// USER DROPDOWN (Page 2 & Page 5)
function toggleUserDropdown(e) {
  e.stopPropagation();
  const menu = document.getElementById("user-dropdown-menu");
  menu.classList.toggle("show");
}

function closeUserDropdown() {
  const menu = document.getElementById("user-dropdown-menu");
  if (menu) menu.classList.remove("show");
}

document.addEventListener("click", (e) => {
  const menu = document.getElementById("user-dropdown-menu");
  const pill = document.querySelector(".nav-user-pill");
  if (menu && !menu.contains(e.target) && (!pill || !pill.contains(e.target))) {
    menu.classList.remove("show");
  }
});

// AUTHENTICATION & SESSION UI
function updateAuthUI() {
  const guestNav = document.getElementById("guest-nav");
  const authNav = document.getElementById("auth-nav");
  const guestBtns = document.getElementById("guest-buttons");
  const authUserBar = document.getElementById("auth-user-bar");

  if (currentUser) {
    if (guestNav) guestNav.style.display = "none";
    if (authNav) authNav.style.display = "flex";
    if (guestBtns) guestBtns.style.display = "none";
    if (authUserBar) authUserBar.style.display = "flex";

    const displayName = (currentSession && currentSession.name) || currentUser.fullName || currentUser.username;
    document.getElementById("nav-user-name").textContent = displayName;
    document.getElementById("dropdown-user-name").textContent = displayName;
    document.getElementById("dropdown-user-role").textContent = (currentSession && currentSession.role) || currentUser.role || "USER";

    const avatarUrl = currentUser.profilePictureUrl || "assets/individual.svg";
    document.getElementById("nav-avatar-img").src = avatarUrl;
    document.getElementById("dropdown-avatar-img").src = avatarUrl;
  } else {
    if (guestNav) guestNav.style.display = "flex";
    if (authNav) authNav.style.display = "none";
    if (guestBtns) guestBtns.style.display = "flex";
    if (authUserBar) authUserBar.style.display = "none";
  }
}

function handleForgotPassword() {
  const modal = document.getElementById("forgot-password-modal");
  const emailInput = document.getElementById("forgot-password-email");
  const loginValue = document.getElementById("login-username").value.trim();
  emailInput.value = loginValue.includes("@") ? loginValue : "";
  document.getElementById("forgot-password-status").textContent = "";
  modal.classList.add("show");
  modal.setAttribute("aria-hidden", "false");
  emailInput.focus();
}

function closeForgotPassword() {
  const modal = document.getElementById("forgot-password-modal");
  modal.classList.remove("show");
  modal.setAttribute("aria-hidden", "true");
}

async function requestPasswordReset(e) {
  e.preventDefault();
  const email = document.getElementById("forgot-password-email").value.trim();
  const status = document.getElementById("forgot-password-status");
  const submitButton = e.currentTarget.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  status.textContent = "Sending reset link…";

  try {
    const res = await fetch("/api/auth/forgot-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email })
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.error || "Could not send the reset link.");
    }
    status.textContent = data.message;
  } catch (err) {
    status.textContent = err.message;
  } finally {
    submitButton.disabled = false;
  }
}

async function handlePasswordReset(e) {
  e.preventDefault();
  const newPassword = document.getElementById("reset-new-password").value;
  const confirmPassword = document.getElementById("reset-confirm-password").value;
  const status = document.getElementById("password-reset-status");
  if (newPassword !== confirmPassword) {
    status.textContent = "The passwords do not match.";
    return;
  }

  const submitButton = e.currentTarget.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  status.textContent = "Updating password…";
  try {
    const res = await fetch("/api/auth/reset-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        token: document.getElementById("password-reset-token").value,
        newPassword
      })
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.error || "Could not update the password.");
    }
    window.history.replaceState({}, document.title, window.location.pathname);
    document.getElementById("login-username").value = "";
    document.getElementById("login-password").value = "";
    navigateTo("login");
    alert(data.message);
  } catch (err) {
    status.textContent = err.message;
  } finally {
    submitButton.disabled = false;
  }
}

async function handleLogin(e) {
  e.preventDefault();
  const username = document.getElementById("login-username").value.trim();
  const password = document.getElementById("login-password").value;
  const status = document.getElementById("login-status");
  const submitButton = e.currentTarget.querySelector('button[type="submit"]');
  status.textContent = "";
  submitButton.disabled = true;
  submitButton.textContent = "Signing in...";

  try {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    if (res.ok) {
      currentUser = await res.json();
      currentSession = {
        id: currentUser.id,
        name: currentUser.fullName || currentUser.username,
        role: currentUser.role || "USER",
        token: currentUser.sessionToken
      };
      updateAuthUI();
      document.getElementById("login-password").value = "";
      navigateTo(getRoleHomeView(currentSession.role));
    } else {
      const err = await res.json().catch(() => ({}));
      status.textContent = err.error || "Invalid account name or password.";
    }
  } catch (err) {
    status.textContent = "Could not reach TalkCare. Make sure the app is running, then try again.";
    console.error("Login request failed:", err);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "SUBMIT";
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const fullName = document.getElementById("reg-name").value.trim();
  const username = document.getElementById("reg-username").value.trim();
  const address = document.getElementById("reg-address").value.trim();
  const contactNo = document.getElementById("reg-contact").value.trim();
  const email = document.getElementById("reg-email").value.trim();
  const password = document.getElementById("reg-password").value;
  const status = document.getElementById("register-status");
  const submitButton = e.currentTarget.querySelector('button[type="submit"]');
  status.textContent = "";
  submitButton.disabled = true;
  submitButton.textContent = "Creating account...";

  try {
    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fullName, username, address, contactNo, email, password })
    });

    if (res.ok) {
      currentUser = await res.json();
      currentSession = {
        id: currentUser.id,
        name: currentUser.fullName || currentUser.username,
        role: currentUser.role || "USER",
        token: currentUser.sessionToken
      };
      updateAuthUI();
      document.getElementById("reg-password").value = "";
      document.getElementById("id-email").value = currentUser.email || email;
      document.getElementById("id-address").value = currentUser.address || address;
      uploadedIdDocBase64 = "";
      document.getElementById("id-type-select").value = "";
      document.getElementById("id-number").value = "";
      document.getElementById("id-preview-img").removeAttribute("src");
      document.getElementById("id-preview-img").style.display = "none";
      document.getElementById("id-upload-prompt").style.display = "block";
      document.getElementById("id-doc-file-input").value = "";
      navigateTo("id-verify");
    } else {
      const err = await res.json().catch(() => ({}));
      status.textContent = err.error || "Please check your details and try again.";
    }
  } catch (err) {
    status.textContent = "Could not reach TalkCare. Make sure the app is running, then try again.";
    console.error("Registration request failed:", err);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Create Account";
  }
}

async function logout() {
  const token = currentSession && currentSession.token;
  if (token) {
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        headers: { "X-Session-Token": token }
      });
    } catch (err) {
      console.error("Logout request failed:", err);
    }
  }
  currentSession = null;
  currentUser = null;
  updateAuthUI();
  navigateTo("welcome");
}

// PROFILE PICTURE UPLOAD & PROFILE UPDATE (Page 4)
function populateProfileFields() {
  if (!currentUser) return;
  document.getElementById("profile-username").value = currentUser.username || "";
  document.getElementById("profile-email").value = currentUser.email || "";
  document.getElementById("profile-display-img").src = currentUser.profilePictureUrl || "assets/individual.svg";
}

async function handleProfilePhotoSelect(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = async (event) => {
    const base64Data = event.target.result;
    document.getElementById("profile-display-img").src = base64Data;
    document.getElementById("nav-avatar-img").src = base64Data;
    document.getElementById("dropdown-avatar-img").src = base64Data;

    try {
      const res = await fetch("/api/profile/upload-photo", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId: currentUser.id, image: base64Data })
      });
      if (res.ok) {
        const data = await res.json();
        currentUser.profilePictureUrl = data.url;
      }
    } catch (err) {
      console.error("Upload photo error:", err);
    }
  };
  reader.readAsDataURL(file);
}

async function updateProfileInfo() {
  const username = document.getElementById("profile-username").value.trim();
  const email = document.getElementById("profile-email").value.trim();
  const password = document.getElementById("profile-password").value.trim();

  try {
    const res = await fetch("/api/profile/update", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: currentUser.id,
        username,
        email,
        password,
        profilePictureUrl: currentUser.profilePictureUrl || ""
      })
    });
    if (res.ok) {
      currentUser.username = username;
      currentUser.email = email;
      updateAuthUI();
      alert("Profile updated successfully!");
    }
  } catch (err) {
    alert("Error updating profile: " + err.message);
  }
}

// ID DOCUMENT SELECTION & UPLOAD (Page 1 bottom -> Page 2 top)
let uploadedIdDocBase64 = "";

function handleIdFileSelect(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (event) => {
    uploadedIdDocBase64 = event.target.result;
    const preview = document.getElementById("id-preview-img");
    const prompt = document.getElementById("id-upload-prompt");
    preview.src = uploadedIdDocBase64;
    preview.style.display = "block";
    prompt.style.display = "none";
  };
  reader.onerror = () => {
    uploadedIdDocBase64 = "";
    alert("Could not read the selected ID image. Please choose the image again.");
  };
  reader.readAsDataURL(file);
}

async function handleIdVerification(e) {
  e.preventDefault();
  if (!currentUser) {
    alert("Please create an account or log in before submitting ID verification.");
    navigateTo("register");
    return;
  }

  const idType = document.getElementById("id-type-select").value;
  const idNumber = document.getElementById("id-number").value.trim();
  const address = document.getElementById("id-address").value.trim();
  const email = document.getElementById("id-email").value.trim();
  if (!uploadedIdDocBase64) {
    alert("Please upload a picture or scan of your ID before submitting.");
    return;
  }

  try {
    const res = await fetch("/api/verification/submit-id", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: currentUser.id,
        idType,
        idNumber,
        address,
        email,
        idDocImage: uploadedIdDocBase64
      })
    });

    if (res.ok) {
      currentUser.idType = idType;
      currentUser.idNumber = idNumber;
      currentUser.address = address;
      currentUser.email = email;
      currentUser.status = "PENDING_VERIFICATION";
      updateAuthUI();
      navigateTo("pending");
    } else {
      const error = await res.json().catch(() => ({}));
      alert("Could not submit ID verification: " + (error.error || "Please check your details and try again."));
    }
  } catch (err) {
    alert("Error: " + err.message);
  }
}

// ABOUT US TAB SWITCHER (Page 2 & 3)
const aboutTabContents = {
  "about-us": {
    heading: "High-quality therapy that drives client outcomes",
    image: "assets/about-us-therapy.jpg",
    imageAlt: "A therapist talking with a client",
    paragraphs: [
      "Talkcare is a mental health and justice-support focused web application built on the belief that every person has the right to feel safe, heard, and respected no matter who they are or where they come from.",
      "We exist to bridge the gap between people and quality mental health support. Our platform creates a judgment-free, inclusive space where anyone can freely express their thoughts, emotions, and experiences without fear of discrimination. We combine compassionate care with clear information helping individuals not only nurture their mental well-being but also understand and stand firm in their rights."
    ]
  },
  "career": {
    heading: "Build a Movement for Fair & Compassionate Care",
    image: "assets/career-team-cutout.png",
    imageAlt: "A team collaborating around a table",
    paragraphs: [
      "At Talkcare, we don’t just build services — we build a movement for compassionate, fair, and inclusive mental health care. We are always looking for passionate, purpose-driven individuals who believe that everyone deserves to feel heard, supported, and valued.",
      "Help us empower more lives, break barriers, and build a future where no one seeks support alone. Together, we make a real difference — one life, one community at a time."
    ]
  },
  "social-impact": {
    heading: "Dignity, Equity, and Individual Empowerment",
    image: "assets/social-impact-cutout.png",
    imageAlt: "A counselor speaking with a student",
    paragraphs: [
      "Talkcare puts clients’ well-being, dignity, and rights at the center of every service. Users gain a trusted, judgment-free space to share their thoughts and feelings, helping them feel validated, understood, and less alone. Clients receive personalized guidance, reliable resources, and clear information on their rights, equipping them to make informed decisions and advocate for themselves with confidence.",
      "Every interaction is designed to respect individuality, privacy, and fairness. Clients experience consistent, respectful care that honors their unique stories and circumstances. Over time, they build greater self-awareness, emotional resilience, and a stronger sense of agency knowing they deserve support, they have rights, and their well-being truly matters."
    ]
  },
  "client-impact": {
    heading: "Empowering Clients to Advocate for Themselves",
    image: "assets/client-impact-cutout.png",
    imageAlt: "A client talking with a mental health professional",
    paragraphs: [
      "Knowledge, understanding, and awareness are powerful tools for mental well-being. Articles and tools are crafted to inform, inspire, and empower making mental health information clear, relatable, and accessible to everyone.",
      "By connecting mental well-being with fairness and justice, the application empowers clients to seek help, understand their rights, feel heard, and receive respectful and appropriate support."
    ]
  },
  "articles": {
    heading: "Mental Health Rights & Coping Strategies",
    image: "assets/articles-mental-health-cutout.png",
    imageAlt: "Mental health definition illustration",
    paragraphs: [
      "Here, you’ll find trusted content covering practical coping strategies, emotional care, and insights on mental health rights and fairness. We break down important topics from managing stress and building resilience to recognizing when to seek help and understanding your rights as someone seeking care. Our goal is to remove stigma, answer your questions, and guide you with kindness and accuracy.",
      "Articles are updated weekly by certified practitioners and human rights advocates to ensure complete relevance and accuracy."
    ]
  }
};

function switchAboutTab(e, tabKey) {
  document.querySelectorAll(".about-tab-btn").forEach(b => b.classList.remove("active"));
  e.currentTarget.classList.add("active");

  const data = aboutTabContents[tabKey];
  if (!data) return;

  const container = document.getElementById("about-tab-text");
  const image = document.getElementById("about-tab-image");
  image.src = data.image;
  image.alt = data.imageAlt;
  container.innerHTML = `
    <h3 class="about-subheading">${data.heading}</h3>
    ${data.paragraphs.map(p => `<p class="about-paragraph">${p}</p>`).join("")}
  `;
}

// CONTACT US FORM
function handleContactSubmit() {
  const msg = document.getElementById("contact-message-text").value.trim();
  if (!msg) {
    alert("Please write your message.");
    return;
  }
  alert("Thank you! Your message has been dispatched to TalkCare Support.");
  document.getElementById("contact-message-text").value = "";
  navigateTo("home");
}

// CHAT / MESSENGER & AUTO-REPLY SYSTEM (Page 6 & 7)
async function openServiceChat(serviceType) {
  const service = chatServices[serviceType];
  if (!service) {
    renderChatNotice("Please choose a valid TalkCare service.");
    navigateTo("chat");
    return;
  }

  currentService = serviceType;
  currentConversationId = null;
  navigateTo("chat");
  renderChatNotice("Loading your conversation...");

  try {
    await selectConversation(serviceType);
  } catch (err) {
    console.error("Conversation fetch error:", err);
    renderChatNotice("Could not load this conversation: " + err.message);
  }
}

async function selectConversation(serviceType) {
  const service = chatServices[serviceType];
  if (!service) {
    throw new Error("Please choose a valid TalkCare service.");
  }

  if (messageRefreshTimer !== null) {
    clearInterval(messageRefreshTimer);
    messageRefreshTimer = null;
  }

  currentService = serviceType;
  currentConversationId = null;
  pendingChatAttachment = null;
  document.getElementById("chat-text-input").value = "";
  document.getElementById("chat-file-input").value = "";
  document.getElementById("chat-send-status").textContent = "";
  document.getElementById("chat-search-box").value = "";
  renderPendingChatAttachment();
  document.getElementById("chat-messages-box").replaceChildren();

  document.getElementById("chat-service-seal").src = service.seal;
  document.getElementById("chat-service-header-title").textContent = service.name;
  document.getElementById("chat-header-avatar").src = service.avatar;
  document.getElementById("chat-header-name").textContent = service.professionalName;
  document.getElementById("chat-text-input").placeholder = service.placeholder;

  const thread = document.createElement("button");
  thread.type = "button";
  thread.className = "chat-thread-item active";
  thread.innerHTML = `
    <img src="${service.avatar}" alt="" class="chat-thread-avatar">
    <span class="chat-thread-meta">
      <span class="chat-thread-name">${service.professionalName}</span>
      <span class="chat-thread-preview">${service.preview}</span>
    </span>
  `;
  thread.onclick = () => loadConversationMessages();
  document.getElementById("chat-thread-list").replaceChildren(thread);

  const res = await apiFetch(`/api/conversations?serviceType=${encodeURIComponent(serviceType)}`);
  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.error || "The conversation could not be loaded.");
  }
  const conversation = await res.json();
  currentConversationId = conversation.id;
  await loadConversationMessages();
  messageRefreshTimer = setInterval(() => loadConversationMessages(true), 4000);
}

async function loadConversationMessages(silent = false) {
  if (currentConversationId === null) return;

  try {
    const res = await apiFetch(`/api/messages?conversationId=${currentConversationId}`);
    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.error || "Messages could not be loaded.");
    }
    const messages = await res.json();
    renderChatMessages(messages);
  } catch (err) {
    if (!silent) {
      console.error("Messages load error:", err);
      renderChatNotice(err.message);
    }
  }
}

function renderChatNotice(message) {
  const container = document.getElementById("chat-messages-box");
  const notice = document.createElement("div");
  notice.className = "chat-empty-state";
  notice.textContent = message;
  container.replaceChildren(notice);
}

function renderChatMessages(messages, container = document.getElementById("chat-messages-box")) {
  container.replaceChildren();

  if (messages.length === 0) {
    const emptyState = document.createElement("div");
    emptyState.className = "chat-empty-state";
    emptyState.textContent = "Your messages will appear here. Send a message to start the conversation with your assigned professional.";
    container.appendChild(emptyState);
    return;
  }

  messages.forEach(m => {
    const row = document.createElement("div");
    const isProfessional = currentSession
      && (currentSession.role === "THERAPIST" || currentSession.role === "CASE_HANDLER");
    const isOwnMessage = m.senderRole === "SYSTEM"
      ? false
      : (isProfessional ? m.senderRole === "PROFESSIONAL" : m.senderRole === "CLIENT");
    const roleClass = m.senderRole === "SYSTEM" ? "system" : (isOwnMessage ? "client" : "professional");
    row.className = `message-row ${roleClass}`;

    const service = chatServices[currentService];
    const avatarImg = m.senderRole === "CLIENT"
      ? ((currentUser && currentUser.profilePictureUrl) || "assets/individual.svg")
      : service.avatar;

    const timeStr = formatTime(m.createdAt);
    const avatar = document.createElement("img");
    avatar.src = avatarImg;
    avatar.alt = "";
    avatar.className = "message-avatar-small";
    const content = document.createElement("div");
    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    if (m.messageText) {
      const text = document.createElement("div");
      text.textContent = m.messageText;
      bubble.appendChild(text);
    }
    if (m.attachmentUrl && m.attachmentUrl.startsWith("/uploads/")) {
      const attachment = document.createElement("img");
      attachment.src = m.attachmentUrl;
      attachment.alt = "Photo attached to this message";
      attachment.className = "message-attachment";
      bubble.appendChild(attachment);
    }
    const time = document.createElement("div");
    time.className = "message-time";
    time.textContent = timeStr;
    content.append(bubble, time);
    row.append(avatar, content);
    container.appendChild(row);
  });

  // Scroll to bottom
  container.scrollTop = container.scrollHeight;
}

/**
 * Chat submission requirement:
 * "dun sa chatbot, kapag nag message ang user or client, dapat may auto reply na
 * 'thank you for your message, please wait for the reply of the assigned Professional',
 * then dapat pag nakita na ng professional ang message ay makakapagreply sila"
 */
async function handleSendMessage(e) {
  e.preventDefault();
  const input = document.getElementById("chat-text-input");
  const text = input.value.trim();
  if (!text && !pendingChatAttachment) return;
  if (currentConversationId === null) {
    alert("Please select a service before sending a message.");
    return;
  }
  const submitButton = e.currentTarget.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  document.getElementById("chat-send-status").textContent = "";
  try {
    const res = await apiFetch("/api/messages/send", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        conversationId: currentConversationId,
        messageText: text,
        attachmentData: pendingChatAttachment ? pendingChatAttachment.dataUrl : ""
      })
    });
    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.error || "The message could not be sent.");
    }
    renderChatMessages(await res.json());
    input.value = "";
    pendingChatAttachment = null;
    document.getElementById("chat-file-input").value = "";
    renderPendingChatAttachment();
    await loadConversationMessages(true);
  } catch (err) {
    document.getElementById("chat-send-status").textContent = "Message not sent: " + err.message;
    console.error("Message send error:", err);
  } finally {
    submitButton.disabled = false;
  }
}

function getProfessionalInbox() {
  if (!currentSession) return null;
  const viewId = getRoleHomeView(currentSession.role);
  return document.querySelector(`#view-${viewId} .professional-inbox`);
}

async function loadProfessionalInbox() {
  const inbox = getProfessionalInbox();
  if (!inbox) return;

  const status = inbox.querySelector(".professional-inbox-status");
  try {
    const res = await apiFetch("/api/professional/conversations");
    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.error || "Assigned conversations could not be loaded.");
    }

    professionalConversations = await res.json();
    renderProfessionalConversationList(inbox);

    if (professionalConversationId !== null
        && !professionalConversations.some(conversation => conversation.id === professionalConversationId)) {
      professionalConversationId = null;
    }
    if (professionalConversationId === null && professionalConversations.length > 0) {
      await selectProfessionalConversation(professionalConversations[0].id);
    } else if (professionalConversationId !== null) {
      await loadProfessionalConversationMessages(true);
    } else {
      inbox.querySelector(".professional-active-title").textContent = "No assigned conversations yet";
      inbox.querySelector(".professional-message-list").replaceChildren();
      inbox.querySelector(".professional-reply-input").disabled = true;
      inbox.querySelector(".professional-reply-send").disabled = true;
    }
    status.textContent = "";
  } catch (err) {
    status.textContent = "Could not load messages: " + err.message;
    console.error("Professional inbox load error:", err);
  }
}

function renderProfessionalConversationList(inbox = getProfessionalInbox()) {
  if (!inbox) return;
  const list = inbox.querySelector(".professional-conversation-list");
  list.replaceChildren();

  if (professionalConversations.length === 0) {
    const empty = document.createElement("p");
    empty.className = "professional-inbox-empty";
    empty.textContent = "No assigned conversations yet. New client messages will appear here.";
    list.appendChild(empty);
    return;
  }

  professionalConversations.forEach(conversation => {
    const service = chatServices[conversation.serviceType];
    const button = document.createElement("button");
    button.type = "button";
    button.className = "professional-conversation-button";
    if (conversation.id === professionalConversationId) button.classList.add("active");
    const client = document.createElement("strong");
    client.textContent = `Client #${conversation.clientId}`;
    const serviceLabel = document.createElement("span");
    serviceLabel.textContent = service ? service.name : conversation.serviceType;
    button.append(client, serviceLabel);
    button.onclick = () => selectProfessionalConversation(conversation.id);
    list.appendChild(button);
  });
}

async function selectProfessionalConversation(conversationId) {
  const inbox = getProfessionalInbox();
  const conversation = professionalConversations.find(item => item.id === conversationId);
  if (!inbox || !conversation) return;

  professionalConversationId = conversation.id;
  currentService = chatServices[conversation.serviceType] ? conversation.serviceType : "JUSTICE_CARE";
  inbox.querySelector(".professional-active-title").textContent =
    `${chatServices[currentService].name} · Client #${conversation.clientId}`;
  inbox.querySelector(".professional-reply-input").disabled = false;
  inbox.querySelector(".professional-reply-send").disabled = false;
  renderProfessionalConversationList(inbox);
  await loadProfessionalConversationMessages();
}

async function loadProfessionalConversationMessages(silent = false) {
  if (professionalConversationId === null) return;
  const inbox = getProfessionalInbox();
  if (!inbox) return;

  try {
    const res = await apiFetch(`/api/messages?conversationId=${professionalConversationId}`);
    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.error || "Messages could not be loaded.");
    }
    renderChatMessages(await res.json(), inbox.querySelector(".professional-message-list"));
  } catch (err) {
    if (!silent) {
      inbox.querySelector(".professional-inbox-status").textContent = "Could not load this conversation: " + err.message;
      console.error("Professional conversation load error:", err);
    }
  }
}

async function handleProfessionalReply(e) {
  e.preventDefault();
  const inbox = getProfessionalInbox();
  if (!inbox || professionalConversationId === null) return;

  const input = inbox.querySelector(".professional-reply-input");
  const button = inbox.querySelector(".professional-reply-send");
  const messageText = input.value.trim();
  if (!messageText) return;

  button.disabled = true;
  inbox.querySelector(".professional-inbox-status").textContent = "";
  try {
    const res = await apiFetch("/api/messages/reply", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ conversationId: professionalConversationId, messageText })
    });
    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.error || "The reply could not be sent.");
    }
    input.value = "";
    await loadProfessionalConversationMessages(true);
  } catch (err) {
    inbox.querySelector(".professional-inbox-status").textContent = "Reply not sent: " + err.message;
    console.error("Professional reply error:", err);
  } finally {
    button.disabled = false;
  }
}

// PROFESSIONAL CREDENTIALS MODAL (Page 6 & 7)
function openProfessionalModal() {
  const service = chatServices[currentService];
  const modal = document.getElementById("prof-modal");
  const img = document.getElementById("prof-modal-img");
  const title = document.getElementById("prof-modal-title-name");
  const subtag = document.getElementById("prof-modal-subtag");
  const certWrap = document.getElementById("prof-cert-wrap");

  img.src = service.avatar;
  title.textContent = service.professionalName;
  subtag.textContent = service.professionalTitle;
  certWrap.style.display = currentService === "JUSTICE_CARE" ? "none" : "block";

  modal.classList.add("show");
}

function closeProfessionalModal(e) {
  if (e && e.target !== e.currentTarget && !e.target.classList.contains("btn-close-modal")) {
    return;
  }
  document.getElementById("prof-modal").classList.remove("show");
}

// REPORTS & ACTIVITY HISTORY (Page 8 & Page 9)
async function loadReports() {
  try {
    const res = await apiFetch(`/api/reports/list?filter=${encodeURIComponent(currentReportsFilter)}`);
    if (res.ok) {
      reportsCache = await res.json();
      renderReportsList(reportsCache);
      updateReportPillCounts();
    } else {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.error || "Reports could not be loaded.");
    }
  } catch (err) {
    console.error("Reports load error:", err);
  }
}

function renderReportsList(list) {
  const container = document.getElementById("activity-reports-list");
  container.innerHTML = "";

  if (list.length === 0) {
    container.innerHTML = `<div style="text-align: center; padding: 24px; color: #557053;">No reports found under "${currentReportsFilter}".</div>`;
    return;
  }

  list.forEach(r => {
    const row = document.createElement("div");
    row.className = "activity-item-row";
    row.onclick = () => viewReportDetails(r);

    let badgeClass = "under-review";
    if (r.status === "In Progress") badgeClass = "in-progress";
    if (r.status === "Resolved") badgeClass = "resolved";

    row.innerHTML = `
      <div class="activity-item-left">
        <span class="activity-doc-icon">&#128196;</span>
        <div>
          <div class="activity-item-code">${r.reportCode || "Report"}</div>
          <div class="activity-item-status">${r.status || "Under Review"}.</div>
        </div>
      </div>
      <div>
        <span class="status-badge ${badgeClass}">${r.status}</span>
      </div>
    `;
    container.appendChild(row);
  });
}

function updateReportPillCounts() {
  apiFetch("/api/reports/list?filter=All").then(r => {
    if (!r.ok) throw new Error("Report counts could not be loaded.");
    return r.json();
  }).then(all => {
    const allCount = all.length;
    const reviewCount = all.filter(x => x.status === "Under Review").length;
    const progCount = all.filter(x => x.status === "In Progress").length;
    const resCount = all.filter(x => x.status === "Resolved").length;

    document.getElementById("pill-all").textContent = `All (${allCount})`;
    document.getElementById("pill-review").textContent = `Under Review (${reviewCount})`;
    document.getElementById("pill-progress").textContent = `In Progress (${progCount})`;
    document.getElementById("pill-resolved").textContent = `Resolved (${resCount})`;
  }).catch(() => {});
}

function filterActivityHistory(statusFilter, e) {
  currentReportsFilter = statusFilter;
  if (e) {
    document.querySelectorAll(".activity-pill").forEach(p => p.classList.remove("active"));
    e.currentTarget.classList.add("active");
  }
  loadReports();
}

function viewReportDetails(r) {
  alert(`Report: ${r.reportCode}\nClient: ${r.fullName}\nConcern: ${r.concernType}\nStatus: ${r.status}\nDetails: ${r.concernDetails}`);
}

// REPORT CONCERN SUBMISSION (Page 9)
async function handleReportSubmit(e) {
  e.preventDefault();
  const form = e.currentTarget;
  const submitButton = form.querySelector('button[type="submit"]');
  const status = document.getElementById("report-submit-status");
  const fullName = document.getElementById("report-name").value.trim();
  const email = document.getElementById("report-email").value.trim();
  const address = document.getElementById("report-address").value.trim();
  const contactNo = document.getElementById("report-contact").value.trim();
  const concernDetails = document.getElementById("report-details").value.trim();
  const concernType = document.getElementById("report-type").value;
  const involvedWitness = document.getElementById("report-witness").value.trim();

  status.textContent = "";
  submitButton.disabled = true;
  submitButton.textContent = "SUBMITTING...";
  try {
    const report = {
      fullName,
      email,
      address,
      contactNo,
      concernDetails,
      concernType,
      involvedWitness
    };
    if (currentUser && currentUser.id) report.userId = currentUser.id;

    const res = await apiFetch("/api/reports/submit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(report)
    });

    const response = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(response.error || "Hindi naipasa ang report. Pakisubukan ulit.");
    }

    document.getElementById("report-success-title").textContent =
      `Your Report has been successfully submitted${response.reportCode ? ` (${response.reportCode})` : ""}!`;
    form.reset();
    navigateTo("report-success");
    await loadReports();
  } catch (err) {
    status.textContent = err.message || "Hindi naipasa ang report. Pakisubukan ulit.";
    console.error("Report submission failed:", err);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "SUBMIT";
  }
}

// ADMIN DASHBOARD & SCHEDULE OVERVIEW (Page 10)
let currentAdminRequest = null;
let adminAppointmentDays = new Set();
let adminCalendarDate = new Date();

async function loadAdminDashboard() {
  await Promise.all([
    loadAdminMetrics(),
    loadAdminPendingRequests(),
    loadAdminUsers(),
    loadAdminActivity(),
    loadAdminSchedule()
  ]);
}

async function loadAdminMetrics() {
  try {
    const res = await apiFetch("/api/admin/metrics");
    if (!res.ok) throw new Error(await readAdminError(res));
    const stats = await res.json();
    document.getElementById("stat-total-users").textContent = stats.totalUsers ?? "0";
    document.getElementById("stat-pending-approvals").textContent = stats.pendingApprovals ?? "0";
    document.getElementById("stat-session-today").textContent = stats.sessionToday ?? "0";
  } catch (err) {
    console.error("Metrics load error:", err);
    document.getElementById("stat-total-users").textContent = "—";
    document.getElementById("stat-pending-approvals").textContent = "—";
    document.getElementById("stat-session-today").textContent = "—";
  }
}

async function loadAdminPendingRequests() {
  try {
    const res = await apiFetch("/api/admin/pending-requests");
    if (!res.ok) throw new Error(await readAdminError(res));
    const requests = await res.json();
    const container = document.getElementById("admin-pending-requests-list");
    container.replaceChildren();

    if (!requests.length) {
      container.innerHTML = '<div class="admin-request-empty">No pending requests</div>';
      return;
    }

    requests.forEach(request => {
      const row = document.createElement("div");
      row.className = "admin-request-row";

      const info = document.createElement("div");
      info.className = "admin-request-info";
      const title = document.createElement("div");
      title.className = "admin-request-title";
      title.textContent = request.type === "VERIFICATION"
        ? (request.name || "Verification request")
        : (request.reportCode || "Report/Concern");
      const note = document.createElement("div");
      note.className = "admin-request-note";
      note.textContent = request.type === "VERIFICATION"
        ? `${request.idType || "ID"} ${request.idNumber ? `• ${request.idNumber}` : ""} • Submitted ${formatAdminDate(request.submittedAt)}`
        : `${request.name || "Client"} • ${request.concernType || "Report"} • Submitted ${formatAdminDate(request.submittedAt)}`;
      const description = document.createElement("div");
      description.append(title, note);
      info.appendChild(description);

      if (request.type === "VERIFICATION" && request.imageUrl) {
        const image = document.createElement("img");
        image.className = "admin-request-image";
        image.src = request.imageUrl;
        image.alt = "Submitted ID document";
        info.appendChild(image);
      }

      const review = document.createElement("button");
      review.className = "btn-admin-review";
      review.type = "button";
      review.textContent = "Review";
      review.addEventListener("click", () => openAdminReviewModal(request));
      row.append(info, review);
      container.appendChild(row);
    });
  } catch (err) {
    console.error("Admin pending error:", err);
    showAdminLoadError("admin-pending-requests-list", `Unable to load pending requests: ${err.message}`);
  }
}

function openAdminReviewModal(request) {
  currentAdminRequest = request;
  const isVerification = request.type === "VERIFICATION";
  const setText = (id, value) => {
    const element = document.getElementById(id);
    element.textContent = value || "—";
    const row = element.closest("div[id]");
    if (row) row.hidden = !value;
  };
  setText("review-name", request.name);
  setText("review-email", request.email);
  setText("review-submitted", formatAdminDate(request.submittedAt));
  setText("review-id-type", request.idType);
  setText("review-id-number", request.idNumber);
  setText("review-address", request.address);
  setText("review-contact", request.contactNo);
  setText("review-report-code", request.reportCode);
  setText("review-concern-type", request.concernType);
  setText("review-details", request.details);
  setText("review-witness", request.witness);
  setText("review-existing-remarks", request.remarks);

  ["review-id-type-row", "review-id-number-row", "review-address-row", "review-contact-row",
    "review-report-code-row", "review-concern-type-row", "review-details-row",
    "review-witness-row", "review-existing-remarks-row"].forEach(id => {
    document.getElementById(id).hidden = isVerification
      ? !["review-id-type-row", "review-id-number-row", "review-address-row", "review-contact-row"].includes(id)
      : !["review-report-code-row", "review-concern-type-row", "review-details-row", "review-witness-row", "review-existing-remarks-row"].includes(id);
  });

  const image = document.getElementById("review-image");
  image.hidden = isVerification ? !request.imageUrl : true;
  if (!image.hidden) image.src = request.imageUrl;
  document.getElementById("review-verification-actions").hidden = !isVerification;
  document.getElementById("review-report-actions").hidden = isVerification;
  document.getElementById("review-reason-label").hidden = true;
  document.getElementById("review-reason").hidden = true;
  document.getElementById("review-reason").value = "";
  document.getElementById("review-confirm-reject").hidden = true;
  document.getElementById("review-remarks-label").hidden = true;
  document.getElementById("review-remarks").hidden = true;
  document.getElementById("review-remarks").value = "";
  document.getElementById("review-save-remarks").hidden = true;
  const modal = document.getElementById("admin-review-modal");
  modal.classList.add("show");
  modal.setAttribute("aria-hidden", "false");
}

function closeAdminReviewModal() {
  const modal = document.getElementById("admin-review-modal");
  modal.classList.remove("show");
  modal.setAttribute("aria-hidden", "true");
  currentAdminRequest = null;
}

function showAdminRejectReason() {
  document.getElementById("review-reason-label").hidden = false;
  document.getElementById("review-reason").hidden = false;
  document.getElementById("review-confirm-reject").hidden = false;
}

function showAdminRemarks() {
  document.getElementById("review-remarks-label").hidden = false;
  document.getElementById("review-remarks").hidden = false;
  document.getElementById("review-save-remarks").hidden = false;
}

async function submitAdminReviewAction(action) {
  if (!currentAdminRequest) return;
  const reason = document.getElementById("review-reason").value.trim();
  const remarks = document.getElementById("review-remarks").value.trim();
  if (action === "REJECT" && !reason) {
    alert("Please provide a reason for rejection.");
    document.getElementById("review-reason").focus();
    return;
  }
  if (action === "ADD_REMARKS" && !remarks) {
    alert("Please enter remarks before saving.");
    document.getElementById("review-remarks").focus();
    return;
  }

  const actionButtons = document.querySelectorAll("#admin-review-modal .admin-action-button");
  actionButtons.forEach(button => { button.disabled = true; });
  try {
    const res = await apiFetch("/api/admin/pending-requests/action", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        type: currentAdminRequest.type,
        id: currentAdminRequest.id,
        action,
        reason,
        remarks
      })
    });
    if (!res.ok) throw new Error(await readAdminError(res));
    closeAdminReviewModal();
    await Promise.all([loadAdminDashboard(), loadReports()]);
    alert("Request updated successfully.");
  } catch (err) {
    alert(`Unable to update request: ${err.message}`);
  } finally {
    actionButtons.forEach(button => { button.disabled = false; });
  }
}

async function loadAdminActivity() {
  try {
    const res = await apiFetch("/api/admin/activity");
    if (!res.ok) throw new Error(await readAdminError(res));
    const activities = await res.json();
    const container = document.getElementById("admin-timeline-list");
    container.replaceChildren();
    if (!activities.length) {
      container.innerHTML = '<div class="admin-activity-empty">No recent activity</div>';
      return;
    }
    activities.forEach(activity => {
      const item = document.createElement("div");
      item.className = "admin-timeline-item";
      const dot = document.createElement("span");
      dot.className = "timeline-circle";
      const text = document.createElement("span");
      text.textContent = `${activity.details || activity.action || "Activity"} • ${formatAdminDate(activity.createdAt)}`;
      item.append(dot, text);
      container.appendChild(item);
    });
  } catch (err) {
    console.error("Admin activity load error:", err);
    showAdminLoadError("admin-timeline-list", `Unable to load recent activity: ${err.message}`);
  }
}

async function loadAdminSchedule() {
  try {
    const res = await apiFetch(`/api/admin/schedule?year=${adminCalendarDate.getFullYear()}&month=${adminCalendarDate.getMonth() + 1}`);
    if (!res.ok) throw new Error(await readAdminError(res));
    const schedule = await res.json();
    adminAppointmentDays = new Set((schedule.days || []).map(Number));
    renderCalendar();
  } catch (err) {
    console.error("Admin schedule load error:", err);
    adminAppointmentDays = new Set();
    renderCalendar();
    showAdminLoadError("admin-calendar-grid", `Unable to load schedule: ${err.message}`);
  }
}

function formatAdminDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

async function readAdminError(response) {
  const message = await response.text();
  return message || `Request failed (${response.status})`;
}

function showAdminLoadError(elementId, message) {
  const container = document.getElementById(elementId);
  if (!container) return;
  const error = document.createElement("div");
  error.className = "admin-request-error";
  error.textContent = message;
  container.replaceChildren(error);
}

async function loadAdminUsers() {
  try {
    const res = await apiFetch("/api/admin/users");
    if (res.ok) {
      const users = await res.json();
      const container = document.getElementById("admin-active-users-list");
      container.innerHTML = "";

      users.forEach(u => {
        const row = document.createElement("div");
        row.className = "admin-user-row";
        const avatar = u.profilePictureUrl || (u.role === "PROFESSIONAL" ? "assets/avatar-case-handler.svg" : "assets/individual.svg");
        row.innerHTML = `
          <img src="${avatar}" alt="Avatar" class="admin-user-thumb">
          <div style="flex: 1;">
            <div style="font-weight: 800; font-size: 15px;">${u.fullName || u.username}</div>
            <div style="font-size: 12px; color: #557053;">${u.email} &bull; <b>${u.role}</b> &bull; Status: ${u.status || "VERIFIED"}</div>
          </div>
        `;
        container.appendChild(row);
      });
    }
  } catch (err) {
    console.error("Admin users load error:", err);
    showAdminLoadError("admin-active-users-list", `Unable to load user profiles: ${err.message}`);
  }
}

function renderCalendar() {
  const grid = document.getElementById("admin-calendar-grid");
  if (!grid) return;
  grid.innerHTML = "";
  const year = adminCalendarDate.getFullYear();
  const month = adminCalendarDate.getMonth();
  const monthLabel = document.getElementById("admin-calendar-month");
  if (monthLabel) {
    monthLabel.textContent = adminCalendarDate.toLocaleDateString(undefined, { month: "long", year: "numeric" });
  }
  const firstWeekday = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].forEach(day => {
    const heading = document.createElement("div");
    heading.className = "calendar-weekday";
    heading.textContent = day;
    grid.appendChild(heading);
  });
  for (let blank = 0; blank < firstWeekday; blank++) {
    const spacer = document.createElement("div");
    spacer.className = "calendar-cell calendar-cell-empty";
    grid.appendChild(spacer);
  }
  for (let i = 1; i <= daysInMonth; i++) {
    const cell = document.createElement("div");
    cell.className = "calendar-cell";
    if (adminAppointmentDays.has(i)) cell.classList.add("has-appointment");
    cell.textContent = i;
    grid.appendChild(cell);
  }
}

// DATABASE CONNECTION MANAGEMENT
async function checkDatabaseStatus() {
  try {
    const res = await fetch("/api/db/status");
    if (res.ok) {
      const data = await res.json();
      const dot = document.getElementById("db-status-dot");
      const text = document.getElementById("db-status-text");

      if (data.connected) {
        dot.className = "db-dot";
        text.textContent = "PostgreSQL Connected";
      } else {
        dot.className = "db-dot disconnected";
        text.textContent = "PostgreSQL (Setup)";
      }

      document.getElementById("cfg-host").value = data.host || "localhost";
      document.getElementById("cfg-port").value = data.port || 5432;
      document.getElementById("cfg-dbname").value = data.dbName || "talkcare";
      document.getElementById("cfg-user").value = data.user || "postgres";

      const box = document.getElementById("db-dialog-status-box");
      box.innerHTML = `<b>Status:</b> ${data.message}`;
    }
  } catch (err) {
    console.error("DB status error:", err);
  }
}

function openDbModal() {
  document.getElementById("db-modal").classList.add("show");
  checkDatabaseStatus();
}

function closeDbModal(e) {
  if (e && e.target !== e.currentTarget && !e.target.classList.contains("btn-close-modal")) {
    return;
  }
  document.getElementById("db-modal").classList.remove("show");
}

async function handleDbSave(e) {
  e.preventDefault();
  const host = document.getElementById("cfg-host").value.trim();
  const port = document.getElementById("cfg-port").value.trim();
  const name = document.getElementById("cfg-dbname").value.trim();
  const user = document.getElementById("cfg-user").value.trim();
  const pass = document.getElementById("cfg-pass").value;

  const box = document.getElementById("db-dialog-status-box");
  box.innerHTML = `<i>Connecting to PostgreSQL...</i>`;

  try {
    const res = await fetch("/api/db/reconnect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ host, port, name, user, password: pass })
    });

    if (res.ok) {
      const result = await res.json();
      box.innerHTML = `<b>Result:</b> ${result.message}`;
      await checkDatabaseStatus();
      if (result.connected) {
        alert("Successfully connected to PostgreSQL database!");
        closeDbModal();
        loadReports();
        loadAdminDashboard();
      }
    }
  } catch (err) {
    box.innerHTML = `<b style="color:red">Error:</b> ${err.message}`;
  }
}

// HELPERS
function filterChatThreads(keyword) {
  const val = keyword.toLowerCase();
  document.querySelectorAll(".chat-thread-item").forEach(item => {
    const text = item.textContent.toLowerCase();
    item.style.display = text.includes(val) ? "flex" : "none";
  });
}

function triggerSearch() {
  const query = prompt("Search TalkCare resources, articles, or cases:");
  if (query) {
    alert(`Searching TalkCare Knowledge Base for "${query}"...`);
    navigateTo("about");
  }
}

function handleChatFileUpload(e) {
  const file = e.target.files[0];
  if (!file) return;
  e.target.value = "";
  if (!["image/png", "image/jpeg"].includes(file.type)) {
    alert("Please attach a PNG or JPEG photo.");
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    alert("Please choose a photo smaller than 5 MB.");
    return;
  }

  const reader = new FileReader();
  reader.onload = () => {
    if (typeof reader.result !== "string") {
      alert("The selected photo could not be read.");
      return;
    }
    pendingChatAttachment = { name: file.name, dataUrl: reader.result };
    renderPendingChatAttachment();
  };
  reader.onerror = () => alert("The selected photo could not be read.");
  reader.readAsDataURL(file);
}

function renderPendingChatAttachment() {
  const preview = document.getElementById("chat-attachment-preview");
  preview.replaceChildren();
  preview.hidden = !pendingChatAttachment;
  if (!pendingChatAttachment) return;

  const fileName = document.createElement("span");
  fileName.textContent = `Photo ready to send: ${pendingChatAttachment.name}`;
  const removeButton = document.createElement("button");
  removeButton.type = "button";
  removeButton.textContent = "Remove";
  removeButton.onclick = () => {
    pendingChatAttachment = null;
    renderPendingChatAttachment();
  };
  preview.append(fileName, removeButton);
}

function formatTime(timestamp) {
  if (!timestamp) return "Just now";
  try {
    const d = new Date(timestamp);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch (e) {
    return "Just now";
  }
}
