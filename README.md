# TalkCare Desktop Application (Mental Health & Justice Platform)

Isang kumpletong Desktop Application para sa **TalkCare (Mental Health & Justice Platform)** na binuo ayon sa eksaktong disenyo at wireframes mula sa ibinigay na PDF file (mula Page 1 hanggang Page 10).

---

## 🌟 Mga Tampok at Pagsunod sa PDF Wireframe

1. **Frontend**:
   - **Java / JavaFX** Desktop Application na pinalalakas ng HTML5, CSS3, at JavaScript para sa makinis, modernong visual rendering na 100% tapat sa color scheme, typography, cards, buttons, tabs, at popups ng PDF.
   - Exact color palette: Muted sage green (`#95ad91`), deep forest green (`#23492d`), at crisp card containers (`#eaf3e7`).
   - SVG vector assets na kopyang-kopya sa wireframe:
     * TalkCare Official Tree Logo na may bukas na mga dahon/kamay at aklat
     * Golden Scales of Justice na may Judge Gavel sa Page 1 Justice Card
     * Individual (batang babae na may orange cardigan)
     * Peers (dalawang magkaibigan na nag-uusap)
     * Serene mountain landscape background sa Login screen
     * Compassionate community warm background sa "YOU ARE NOT ALONE."
     * Official Seals para sa **JusticeCare** at **ThoMatt**
     * Case Handler at Therapist verified avatars
     * Adult Mental Health First Aid USA Certificate sa profile modal
     * 1..31 Interactive Calendar Grid sa Admin Dashboard

2. **Backend & Database**:
   - **PostgreSQL** database backend gamit ang official PostgreSQL JDBC Driver (`org.postgresql.Driver`).
   - Binuo ang kumpletong schema sa [`src/main/resources/schema.sql`](src/main/resources/schema.sql):
     * `users` (id, username, full_name, email, password, contact_no, address, role, profile_picture_url, id_type, id_number, id_document_url, status)
     * `reports` (id, report_code, user_id, full_name, email, address, contact_no, concern_type, involved_witness, concern_details, status)
     * `conversations` (id, client_id, professional_id, service_type, title)
     * `messages` (id, conversation_id, sender_id, sender_name, sender_role, message_text, is_auto_reply, attachment_url)
     * `appointments` (id, user_id, professional_id, session_date, session_time, status)
   - May kasamang **In-App PostgreSQL Connection Manager** (`db-status-badge` sa Navbar). Kung kailangang ilagay o baguhin ang password ng PostgreSQL (hal. `postgres`), i-click lamang ang status badge sa itaas upang mag-reconnect at mag-migrate sa isang pindot lang!

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
   - **Page 2**: Pending Request screen na may "Okay" button, "YOU ARE NOT ALONE." Hero section, at User Dropdown Drawer menu (My Profile, Contacts, Messages, Scheduling, About Thomatt, About Justicecare, Log out).
   - **Page 3**: About Us na may 5 functional sub-tabs (About us, Career, Social Impact, Client Impact, Articles).
   - **Page 4**: Profile screen na may photo uploader, Contact Us form na may official TalkCare emblem, at hiwalay na mga artikulo para sa "About Thomatt" at "About Justicecare".
   - **Page 6**: Services screen (Justice Care & ThoMatt cards) at ang JusticeCare Chat interface na may case handler profile modal.
   - **Page 7**: ThoMatt Therapist Chat na may verified badge at official Adult Mental Health First Aid USA certificate popup.
   - **Page 8**: Dashboard na may 6 action tiles (Report a Concern, Resolved, New Notifications, Book a Session, Message, Reports) at Notification / Activity History tabs (`All`, `Under Review`, `In Progress`, `Resolved`).
   - **Page 9**: Report Concern Submission Form at ang Success Screen na may malaking TalkCare tree emblem.
   - **Page 10**: TalkCare Admin Dashboard na may Pending Requests list (`Review` buttons para mag-update ng status), Recent Activity timeline, Interactive Schedule Overview calendar, at Active User Profiles.

6. **Role-Based Login**:
   - `USER` accounts open the existing client Home; `THERAPIST`, `CASE_HANDLER`, and `ADMIN` accounts open their respective dashboards.
   - Login creates a server-side session. Users can read only their own reports; only admins can view all reports, update report status, and access admin data.
   - Startup creates the following test accounts only if the username and email are not already present. Passwords are stored as BCrypt hashes:

     | Role | Email | Password |
     | --- | --- | --- |
     | USER | `test.user@talkcare.com` | `TalkCareUser#2026` |
     | THERAPIST | `test.therapist@talkcare.com` | `TalkCareTherapist#2026` |
     | CASE_HANDLER | `test.casehandler@talkcare.com` | `TalkCareCaseHandler#2026` |
     | ADMIN | `test.admin@talkcare.com` | `TalkCareAdmin#2026` |

---

## 🚀 Paano Patakbuhin ang Application

### Kinakailangan:
- Java JDK 25 (LTS)
- PostgreSQL (Tumatakbo sa `localhost:5432`)
- Maven (Naka-install na sa sistema)

### Paraan 1: Gamit ang Batch Launcher (Pinakamadali)
I-double click lamang ang:
```cmd
run.bat
```
Awtomatiko nitong iko-compile ang mga binagong files at bubuksan ang desktop window ng TalkCare!

### Paraan 2: Gamit ang Maven
Buksan ang terminal sa folder ng TalkCare at patakbuhin:
```cmd
mvn javafx:run
```

---

## 🗄️ Pagsasaayos ng PostgreSQL Database

Ang database credentials ay matatagpuan sa [`src/main/resources/config.properties`](src/main/resources/config.properties) o maaaring i-configure nang direkta sa loob ng app:
1. Sa bandang itaas ng Navbar, i-click ang badge na nagsasabing **"PostgreSQL"**.
2. Ilagay ang iyong PostgreSQL password (karaniwan ay `postgres`, `admin`, o ang napiling password noong nag-install ng PostgreSQL 18).
3. I-click ang **"Test & Connect to PostgreSQL"**.
4. Awtomatikong lilikhain at i-a-apply ng application ang buong schema mula sa `schema.sql`.

## Password recovery email

The **Forgot Password** link sends a one-time reset link to the account email. Reset links expire after 30 minutes and can only be used once. Configure SMTP before starting TalkCare; credentials are read from environment variables and must not be committed to the project:

```powershell
$env:TALKCARE_SMTP_HOST = "smtp.gmail.com"
$env:TALKCARE_SMTP_PORT = "587"
$env:TALKCARE_SMTP_USERNAME = "your-sender@gmail.com"
$env:TALKCARE_SMTP_PASSWORD = "your-provider-app-password"
$env:TALKCARE_SMTP_FROM = "your-sender@gmail.com"
$env:TALKCARE_RESET_URL = "http://localhost:54321"
.\run.bat
```

Use an SMTP app password or provider-issued credential, not an email account's normal sign-in password. The reset email link works while TalkCare is running on the same computer. Without SMTP configuration, the application reports that email recovery is unavailable rather than claiming that an email was sent.
