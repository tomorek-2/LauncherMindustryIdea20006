package singlaunch;

import javafx.application.Platform;
import javafx.stage.Stage;

public final class LauncherWindow {
    private final Stage stage;
    private boolean hiddenForGame;

    public LauncherWindow(Stage stage) {
        this.stage = stage;
        stage.setOnHidden(e -> {
            if (!hiddenForGame) Platform.exit();
        });
    }

    public void hideForGame() {
        hiddenForGame = true;
        Platform.runLater(stage::hide);
    }

    public void showAfterGame() {
        hiddenForGame = false;
        Platform.runLater(() -> {
            stage.show();
            stage.toFront();
            HyprlandSupport.floatNow(stage);
        });
    }
}
