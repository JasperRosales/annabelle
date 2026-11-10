# TalkCare Desktop Application (Mental Health & Justice Platform)

Isang kumpletong Desktop Application para sa **TalkCare (Mental Health & Justice Platform)** na binuo ayon sa eksaktong disenyo at wireframes mula sa ibinigay na PDF file (mula Page 1 hanggang Page 10).

---

## 🌟 Mga Tampok at Pagsunod sa PDF Wireframe

1. **Frontend**:
   - **Java / JavaFX** Desktop Application: HTML5 at CSS3 para sa itsura (100% tapat sa color scheme, typography, cards, buttons, tabs, at popups ng PDF), at **Java** para sa lahat ng behavior. Walang JavaScript ang UI.
   - Exact color palette: Muted sage green (`#95ad91`), deep forest green (`#23492d`), at crisp card containers (`#eaf3e7`).
   - SVG vector assets na kopyang-kopya sa wireframe:
     * TalkCare Official Tree Logo na may bukas na mga dahon/kamay at aklat
     * Golden Scales of Justice na may Judge Gavel sa Page 1 Justice Card
     * Individual (batang babae na may orange cardigan)
     * Peers (dalawang magkaibigan na nag-uusap)
     * Serene mountain landscape background sa Login screen
     * Official Seals para sa **JusticeCare** at **ThoMatt**
     * Case Handler at Therapist verified avatars
     * Adult Mental Health First Aid USA Certificate sa profile modal
     * 1..31 Interactive Calendar Grid sa Admin Dashboard

2. **Backend (hiwalay na repository: `talkcare-api`)**:
   - Ang desktop app ay **UI lamang**. Lahat ng data ay dumadaan sa **TalkCare API** (Express + Neon PostgreSQL), na nasa hiwalay na repository na `talkcare-api`.
   - Walang database password o JDBC connection sa desktop app. Ang schema at migrations ay nasa API repository (`docs/schema.md`, `db/migrations/`).
   - Ang badge sa Navbar ay nagpapakita kung **Online/Offline** ang TalkCare server; i-click ito para makita ang server address.

3. **Messenger / Chatbot na may Auto-Reply at Professional Reply**:
   - Dalawang dedikadong channels alinsunod sa PDF:
     * **JusticeCare**: Legal Rights & Victim Support (Atty. Sofia Ramos), shown only in the JusticeCare conversation.
     * **ThoMatt**: Mental Health & Well-being Consultation (Dr. Eleanor Reyes, R.Psy / L.P.T.), shown only in the ThoMatt conversation.
   - **Eksaktong Auto-Reply Requirement**:
     Kapag nag-send ng mensahe ang user/client, agad na sasagot ang system ng:
     > `"thank you for your message, please wait for the reply of the assigned Professional"`
   - **Client-only view**: Hindi maaaring magpalit ng account o role mula sa chat interface.
   - **Photo attachments**: Maaaring mag-attach at magpadala ng PNG o JPEG na larawan; mase-save ito kasama ng client message at makikita sa conversation.

4. **Upload ng Profile Picture at Pagpili ng ID**:
   - **Profile Screen (Page 4)**: May interactive **"Upload Picture"** camera button kung saan maaaring pumili ng larawan mula sa kompyuter. Magkakaroon agad ito ng real-time preview sa avatar at mase-save sa database at server storage.
   - **ID Verification (Page 1 & 2)**:
     * Dropdown list ng mga valid ID: Student ID, National ID (PhilSys), Driver's License, Passport, Postal ID, PRC ID, UMID.
     * Mga field para sa ID Number, Address, at Email.
     * File selector para sa pag-upload ng imahe o scan ng ID na may interactive preview.
     * Pagka-submit, magtutuloy ito sa **"Pending request"** screen (Page 2) na nagpapakita ng 24-hour review notice bago magpatuloy sa Dashboard!

5. **Lahat ng Pahina mula sa PDF (Page 1 hanggang Page 10)**:
   - **Page 1**: Welcome Landing Screen na may 3 choice cards (Justice, Individual, Peers), Mountain Login Box, Create Account Form, at ID Verification form.
   - **Page 2**: Pending Request screen na may "Okay" button, at User Dropdown Drawer menu (My Profile, Contacts, Messages, Scheduling, About Thomatt, About Justicecare, Log out).
   - **Page 3**: About Us na may 5 functional sub-tabs (About us, Career, Social Impact, Client Impact, Articles).
   - **Page 4**: Profile screen na may photo uploader, Contact Us form na may official TalkCare emblem, at hiwalay na mga artikulo para sa "About Thomatt" at "About Justicecare".
   - **Page 6**: Services screen (Justice Care & ThoMatt cards) at ang JusticeCare Chat interface na may case handler profile modal.
   - **Page 7**: ThoMatt Therapist Chat na may verified badge at official Adult Mental Health First Aid USA certificate popup.
   - **Page 8**: Dashboard na may 6 action tiles (Report a Concern, Resolved, New Notifications, Book a Session, Message, Reports) at Notification / Activity History tabs (`All`, `Under Review`, `In Progress`, `Resolved`).
   - **Page 9**: Report Concern Submission Form at ang Success Screen na may malaking TalkCare tree emblem.
   - **Page 10**: TalkCare Admin Dashboard na may Pending Requests list (`Review` buttons para mag-update ng status), Recent Activity timeline, Interactive Schedule Overview calendar, at Active User Profiles.

6. **Role-Based Login**:
   - Ang server ang nagpapasya ng home view ng bawat account (`dashboardView`): `CLIENT` → Client dashboard, `ATTORNEY` → Case Handler dashboard, `PSYCHIATRIST` → Professional dashboard, `ADMIN` → Admin dashboard.
   - Role names were renamed: `USER → CLIENT`, `CASE_HANDLER → ATTORNEY`, `THERAPIST → PSYCHIATRIST`.
   - **Hiwalay na sign-in page bawat role**: ang "Log in" ay nagbubukas ng portal chooser (Client, Attorney, Psychiatrist, Administrator). Ipinapadala ng bawat page ang `role` sa `/api/auth/login`, kaya tinatanggihan ang account na iba ang role.
   - **Hiwalay na register page**: Client, Attorney (Roll of Attorneys no.), at Psychiatrist (PRC license no.). Pagkatapos mag-register, mag-a-upload ng ID; ang bagong attorney/psychiatrist ay walang client hangga't hindi ina-approve ng admin. Walang public na register page para sa Administrator.
   - Test accounts are created on the API side with `npm run db:seed` (see the `talkcare-api` README):

     | Role | Username | Password |
     | --- | --- | --- |
     | CLIENT | `test.user` | `TalkCareUser#2026` |
     | ATTORNEY | `test.attorney` | `TalkCareCaseHandler#2026` |
     | PSYCHIATRIST | `test.psychiatrist` | `TalkCareTherapist#2026` |
     | ADMIN | `test.admin` | `TalkCareAdmin#2026` |

--- | --- | --- |
     | USER | `test.user@talkcare.com` | `TalkCareUser#2026` |
     | THERAPIST | `test.therapist@talkcare.com` | `TalkCareTherapist#2026` |
     | CASE_HANDLER | `test.casehandler@talkcare.com` | `TalkCareCaseHandler#2026` |
     | ADMIN | `test.admin@talkcare.com` | `TalkCareAdmin#2026` |

---

## 🚀 Paano Patakbuhin ang Application

### Kinakailangan:
- Java JDK 25 (LTS)
- Maven
- Isang tumatakbong **TalkCare API** (`talkcare-api` repository), lokal man o naka-deploy

### Saan kumokonekta ang app
Ang API address ay nasa [`src/main/resources/config.properties`](src/main/resources/config.properties) (`api.baseUrl`, default ang naka-deploy na API sa Render: `https://talkcare-api.onrender.com`). Para sa lokal na API, palitan ito sa paglunsad:

```powershell
$env:TALKCARE_API_URL = "http://localhost:4000"
.\run.bat
```

Ang desktop app ay nagse-serve ng HTML/CSS sa `http://localhost:54321` (`ui.port`) para sa WebView. Ang mga tawag sa API ay ginagawa ng Java (`java.net.http.HttpClient`), kaya hindi kailangan ng CORS setting para sa desktop app.

### Paraan 1: Gamit ang Batch Launcher (Pinakamadali)
I-double click lamang ang:
```cmd
run.bat
```

### Paraan 2: Gamit ang Maven
```cmd
mvn javafx:run
```

## 🧩 Desktop UI architecture (HTML/CSS + Java, no JavaScript)

The window is a JavaFX `WebView` showing the HTML/CSS in `src/main/resources/static`. Everything the page *does* is Java, using the WebView's DOM (`org.w3c.dom`):

- **HTML declares, Java handles.** Elements say what they do with attributes instead of inline JavaScript: `data-action="name" data-arg="..."` (click), `<form data-submit="name">` (submit), `data-input="name"` (typing). `ui/ActionRouter` listens once on the document and dispatches to the controller that registered that name.
- **One page, many files.** `index.html` holds `<div data-include="views/x.html">` slots; `server/PageAssembler` inlines them before the WebView loads the page.
- **Packages**
  - `api`: `TalkCareApi` → `AuthApi`, `ProfileApi`, `ChatApi`, `ReportApi`, `AdminApi`, `HealthApi` (one per API area, sharing `ApiClient`: async HTTP + Jackson JSON).
  - `model`: records for the API data (`Account`, `Conversation`, `Message`, `Report`, `AdminRequest`, ...) and the `ServiceType` enum (JusticeCare / ThoMatt).
  - `ui`: `TalkCareUi` (wires everything), `Navigator` (screens + access rules), `Session` (signed-in account, observers), `UiCall` (API result → JavaFX thread), `Dialogs`, `ImagePicker` (native file chooser), `Poller`.
  - `ui/dom`: `Dom` / `El`, a small fluent wrapper over the WebView DOM.
  - `ui/controller`: one controller per part of the page, all extending `Controller` (`NavbarController`, `AuthController`, `VerificationController`, `ProfileController`, `InfoPagesController`, `ChatController`, `ProfessionalInboxController`, `ReportsController`, `AdminController`, `ConnectionController`). Screens that react to being shown implement `Screen`.
- **OOP:** see [`docs/oop-usage.md`](docs/oop-usage.md) for how encapsulation, abstraction, inheritance and polymorphism are used, with code references.
- **To add a button:** put `data-action="my-action"` on it in the HTML and register `router.onClick("my-action", ...)` in the controller that owns that screen.

## Password recovery email

Forgot password does not send email. The user enters their account email and a new password; the request appears under the admin dashboard's pending requests, and the new password takes effect only when an administrator approves it.
