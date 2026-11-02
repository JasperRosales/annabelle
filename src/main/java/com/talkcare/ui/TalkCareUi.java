package com.talkcare.ui;

import com.talkcare.api.TalkCareApi;
import com.talkcare.ui.controller.AdminController;
import com.talkcare.ui.controller.AppointmentsController;
import com.talkcare.ui.controller.NotificationsController;
import com.talkcare.ui.controller.ReportDetailsController;
import com.talkcare.ui.controller.AuthController;
import com.talkcare.ui.controller.ChatController;
import com.talkcare.ui.controller.ConnectionController;
import com.talkcare.ui.controller.Controller;
import com.talkcare.ui.controller.InfoPagesController;
import com.talkcare.ui.controller.NavbarController;
import com.talkcare.ui.controller.ProfessionalInboxController;
import com.talkcare.ui.controller.ProfileController;
import com.talkcare.ui.controller.ReportsController;
import com.talkcare.ui.controller.UiContext;
import com.talkcare.ui.controller.VerificationController;
import com.talkcare.ui.dom.Dom;
import javafx.stage.Window;
import org.w3c.dom.Document;

import java.util.List;

/**
 * Composition root of the desktop UI: wires the controllers to the loaded page and starts it.
 * Created once the WebView has finished loading the assembled HTML.
 */
public final class TalkCareUi {
    private final Navigator navigator;
    private final NavbarController navbar;
    private final ConnectionController connection;
    private final AuthController auth;
    private final AdminController admin;

    public TalkCareUi(Document document, Window window, TalkCareApi api) {
        Dom dom = new Dom(document);
        Session session = new Session(api.client());
        Dialogs dialogs = new Dialogs(window);
        this.navigator = new Navigator(dom, session);
        UiContext context = new UiContext(dom, api, session, navigator, dialogs, new ImagePicker(window, dialogs));

        VerificationController verification = new VerificationController(context);
        AppointmentsController appointments = new AppointmentsController(context);
        ReportDetailsController reportDetails = new ReportDetailsController(context);
        this.navbar = new NavbarController(context);
        this.connection = new ConnectionController(context);
        this.auth = new AuthController(context, verification);
        this.admin = new AdminController(context, appointments);
        List<Controller> controllers = List.of(
                navbar,
                connection,
                auth,
                verification,
                new ProfileController(context, navbar),
                new InfoPagesController(context),
                new ChatController(context),
                new ProfessionalInboxController(context, appointments, reportDetails),
                new ReportsController(context, appointments, reportDetails),
                appointments,
                reportDetails,
                new NotificationsController(context),
                admin);

        ActionRouter router = new ActionRouter(error -> {
            System.err.println("TalkCare UI error: " + error);
            error.printStackTrace();
            dialogs.error("Something went wrong: " + error.getMessage());
        });
        controllers.forEach(controller -> controller.bind(router));
        router.attach(dom);
    }

    /** Shows the first screen and starts checking the API connection in the background. */
    public void start() {
        admin.renderEmptyCalendar();
        navbar.refresh();
        connection.check(() -> {});
        navigator.navigate("welcome");
    }

    /** Opens the "choose a new password" screen for a token from a reset email. */
    public void openPasswordReset(String token) {
        auth.startPasswordReset(token);
    }
}
