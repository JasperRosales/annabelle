# OOP usage in the TalkCare desktop app

The desktop app is a JavaFX window that shows the TalkCare screens as HTML and CSS. Everything
the screens *do* (navigation, forms, chat, dashboards) is Java, organized around classes and
objects. This document shows where each OOP principle is used, with links to the code.

All paths are under [`src/main/java/com/talkcare`](../src/main/java/com/talkcare).

## Package overview

| Package | Responsibility | Main types |
| --- | --- | --- |
| `api` | Talks to the TalkCare REST API | `TalkCareApi`, `ApiClient`, `AuthApi`, `ProfileApi`, `ChatApi`, `ReportApi`, `AdminApi`, `HealthApi`, `ApiException` |
| `model` | Data returned by the API | `Account`, `Conversation`, `Message`, `Report`, `AdminRequest`, `ServiceType`, ... |
| `ui` | Application wiring and shared UI services | `TalkCareUi`, `Navigator`, `Session`, `Screen`, `ActionRouter`, `UiCall`, `Dialogs`, `ImagePicker`, `Poller` |
| `ui/dom` | Object wrapper over the WebView page | `Dom`, `El` |
| `ui/controller` | One controller per part of the page | `Controller` and its 10 subclasses |
| `server` | Serves the HTML/CSS to the WebView | `StaticUiServer`, `PageAssembler` |

```
                          ┌──────────────┐
                          │  TalkCareUi  │  creates and wires everything
                          └──────┬───────┘
          ┌──────────────────────┼─────────────────────────┐
          ▼                      ▼                         ▼
   ┌─────────────┐      ┌────────────────┐        ┌─────────────────┐
   │  Navigator  │◄─────│   Controller   │───────►│   TalkCareApi   │
   │ (Screen...) │      │   (abstract)   │        │  AuthApi, ...   │
   └─────────────┘      └───────┬────────┘        └────────┬────────┘
                                │ extends                  │ uses
        ┌───────────┬───────────┼───────────┬──────┐       ▼
        ▼           ▼           ▼           ▼      ▼   ┌──────────┐
     Navbar       Auth        Chat        Admin   ...  │ ApiClient│
   Controller  Controller  Controller  Controller      └──────────┘
```

---

## 1. Encapsulation

Each class keeps its state `private` and exposes only the operations other classes need.

**`Session`** ([`ui/Session.java`](../src/main/java/com/talkcare/ui/Session.java)) holds the
signed-in account. Nobody can set it directly; signing in also hands the token to the HTTP client,
so the two can never get out of step:

```java
public final class Session {
    private final ApiClient client;
    private Account account;                      // hidden state

    public void signIn(Account signedIn) {
        account = signedIn;
        client.setSessionToken(signedIn.sessionToken());
        notifyListeners();
    }

    public boolean isProfessional() { ... }
    public String homeView() { ... }
}
```

**`ApiClient`** ([`api/ApiClient.java`](../src/main/java/com/talkcare/api/ApiClient.java)) hides
the `HttpClient`, the Jackson `ObjectMapper`, the token, timeouts and error parsing. Callers only
see `get`, `post` and `patch`.

**`ChatController`** ([`ui/controller/ChatController.java`](../src/main/java/com/talkcare/ui/controller/ChatController.java))
keeps the open conversation, the pending photo and its refresh timer private:

```java
private final Poller poller = new Poller(Duration.seconds(4));
private ServiceType service = ServiceType.JUSTICE_CARE;
private Long conversationId;
private ImageFile attachment;
```

**Immutable records.** The `model` types are Java `record`s: their fields cannot change after
creation. Changes produce a new object instead, e.g. `Account.withProfilePicture(url)`.

---

## 2. Abstraction

Complex details are hidden behind small, meaningful methods.

**`El`** ([`ui/dom/El.java`](../src/main/java/com/talkcare/ui/dom/El.java)) wraps a raw
`org.w3c.dom.Element`. Controllers write

```java
byId("login-status").text("Invalid account name or password.");
byId("id-preview-img").src(file.dataUrl()).display("block");
```

instead of casting to `HTMLInputElement`, editing `style` strings or looping over child nodes.

**`UiCall`** ([`ui/UiCall.java`](../src/main/java/com/talkcare/ui/UiCall.java)) hides threading.
An API call runs in the background, and its result is delivered on the JavaFX thread:

```java
UiCall.of(api().auth().login(username, password))
        .onSuccess(account -> { session().signIn(account); navigator().goHome(); })
        .onError(error -> status.text(error.getMessage()))
        .always(() -> button.disabled(false).text("SUBMIT"))
        .start();
```

**`TalkCareApi`** ([`api/TalkCareApi.java`](../src/main/java/com/talkcare/api/TalkCareApi.java))
groups the REST endpoints by area (`auth()`, `profile()`, `chat()`, `reports()`, `admin()`,
`health()`). No controller builds a URL or JSON body itself.

**`ServiceType`** ([`model/ServiceType.java`](../src/main/java/com/talkcare/model/ServiceType.java))
is an enum: each constant (`JUSTICE_CARE`, `THOMATT`) carries its display name, professional,
seal, avatar and placeholder text, so the chat screen asks the object instead of looking values up.

---

## 3. Inheritance

All page controllers extend the abstract base class **`Controller`**
([`ui/controller/Controller.java`](../src/main/java/com/talkcare/ui/controller/Controller.java)):

```java
public abstract class Controller {
    private final UiContext context;

    protected Controller(UiContext context) { this.context = context; }

    /** Every controller must register the actions it handles. */
    public abstract void bind(ActionRouter router);

    // Inherited helpers
    protected El byId(String id) { ... }
    protected TalkCareApi api() { ... }
    protected Session session() { ... }
    protected Navigator navigator() { ... }
    protected Dialogs dialogs() { ... }
    protected String assetUrl(String url) { ... }
    protected static El submitButton(El form) { ... }
}
```

The ten subclasses reuse those helpers and only add what is specific to their screen:

| Subclass | Part of the page |
| --- | --- |
| `NavbarController` | top bar, user menu, search, navigation links |
| `ConnectionController` | Online/Offline badge and server dialog |
| `AuthController` | log in, register, log out, password reset |
| `VerificationController` | ID verification after registration |
| `ProfileController` | profile photo and account details |
| `InfoPagesController` | About-us tabs, Contact form |
| `ChatController` | client chat with JusticeCare / ThoMatt |
| `ProfessionalInboxController` | attorney and psychiatrist dashboards |
| `ReportsController` | client dashboard: reports and report form |
| `AdminController` | admin dashboard and review dialog |

---

## 4. Polymorphism

**Through the abstract class.** `TalkCareUi`
([`ui/TalkCareUi.java`](../src/main/java/com/talkcare/ui/TalkCareUi.java)) keeps all controllers
in one `List<Controller>` and treats them the same way; each object runs its own version of `bind`:

```java
List<Controller> controllers = List.of(navbar, connection, auth, verification,
        new ProfileController(context, navbar), new InfoPagesController(context),
        new ChatController(context), new ProfessionalInboxController(context),
        new ReportsController(context), admin);

controllers.forEach(controller -> controller.bind(router));   // dynamic dispatch
```

**Through an interface.** Controllers that load data when their view opens implement
**`Screen`** ([`ui/Screen.java`](../src/main/java/com/talkcare/ui/Screen.java)):

```java
public interface Screen {
    void onShow(String viewId);
    default void onHide() {}
}
```

`Navigator` ([`ui/Navigator.java`](../src/main/java/com/talkcare/ui/Navigator.java)) only knows
`Screen`. It calls the same two methods on whichever object owns the view, and each one responds
differently:

```java
if (active != null) active.onHide();     // e.g. ChatController stops polling
...
Screen screen = screens.get(viewId);
if (screen != null) screen.onShow(viewId);   // e.g. AdminController loads metrics
```

| `Screen` implementation | `onShow` does | `onHide` does |
| --- | --- | --- |
| `ChatController` | loads messages, starts 4 s polling | stops polling |
| `ProfessionalInboxController` | loads assigned conversations, starts polling | stops polling |
| `ReportsController` | loads the reports and status counts | (default: nothing) |
| `AdminController` | loads metrics, requests, users, activity, schedule | (default: nothing) |
| `ProfileController` | fills the profile form | (default: nothing) |

The `default` method on the interface is also polymorphism: screens without polling simply
inherit the empty `onHide()`.

---

## Other object-oriented design in the code

- **Composition.** `TalkCareApi` *has* an `ApiClient` and six service objects; `ChatController`
  *has* a `MessageListView` and a `Poller`; `AdminController` *has* an `AdminCalendar`.
  Behaviour is built by combining objects rather than one large class.
- **Single responsibility.** Each class has one job: `Navigator` switches screens, `Session`
  tracks the user, `ImagePicker` picks files, `PageAssembler` builds the page.
- **Observer pattern.** `Session.addListener(...)` lets `NavbarController` update itself whenever
  someone signs in, signs out or changes their photo, without `Session` knowing about the navbar.
- **Builder-style fluent API.** `UiCall` and `El` return `this`, so calls can be chained.
- **Dependency injection.** Controllers receive what they need (`UiContext`, other controllers)
  through their constructors, which `TalkCareUi` (the composition root) supplies.
- **Custom exception type.** `ApiException` extends `RuntimeException` and adds the HTTP status
  and `isUnreachable()`, so the UI can show "server unreachable" differently from a server error.

## Where the design could go further

- Roles are strings on `Account` (`"ATTORNEY"`, `"PSYCHIATRIST"`); a `Role` enum would make
  them type-safe.
- `AdminRequest` uses a `type` field (`"VERIFICATION"` / `"REPORT"`) and `AdminController`
  branches on it; a sealed `AdminRequest` interface with `VerificationRequest` and
  `ReportRequest` would replace that branching with polymorphism.
- `ReportsController` maps report filters with a `Map<String, String[]>`; a `ReportFilter`
  enum would be clearer.
