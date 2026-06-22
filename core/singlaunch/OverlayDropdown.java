package singlaunch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.control.ListView;
import javafx.scene.web.WebView;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class OverlayDropdown {
    private static final Gson GSON = new Gson();
    private static final Type ITEMS_TYPE = new TypeToken<List<Map<String, String>>>() {}.getType();
    private static Popup activePopup;

    private OverlayDropdown() {}

    public static void show(WebView webView, String itemsJson, String selectedId, Consumer<String> onSelect) {
        Platform.runLater(() -> {
            close();

            List<Map<String, String>> items = GSON.fromJson(itemsJson, ITEMS_TYPE);
            if (items == null || items.isEmpty()) return;

            ListView<String> list = new ListView<>();
            list.setItems(FXCollections.observableArrayList(
                    items.stream().map(m -> m.getOrDefault("label", m.getOrDefault("id", ""))).toList()));
            list.setPrefWidth(300);
            list.setPrefHeight(Math.min(260, Math.max(80, items.size() * 30 + 8)));
            list.setStyle(
                    "-fx-background-color: #232323;" +
                    "-fx-control-inner-background: #232323;" +
                    "-fx-text-fill: #e8e8e8;" +
                    "-fx-border-color: #ffd379;" +
                    "-fx-border-width: 1;");

            int selectedIndex = 0;
            for (int i = 0; i < items.size(); i++) {
                if (selectedId != null && selectedId.equals(items.get(i).get("id"))) {
                    selectedIndex = i;
                    break;
                }
            }
            list.getSelectionModel().select(selectedIndex);
            list.scrollTo(selectedIndex);

            Popup popup = new Popup();
            popup.setAutoHide(true);
            popup.setAutoFix(false);
            popup.getContent().add(list);

            list.setOnMouseClicked(event -> {
                int idx = list.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < items.size()) {
                    onSelect.accept(items.get(idx).get("id"));
                }
                popup.hide();
                activePopup = null;
            });

            Bounds bounds = webView.localToScreen(webView.getBoundsInLocal());
            double x = bounds.getMinX() + 40;
            double y = bounds.getMinY() + 120;
            double popupHeight = list.getPrefHeight();
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            if (y + popupHeight > screen.getMaxY() - 8) {
                y = y - popupHeight - 36;
            }

            Window owner = webView.getScene() != null ? webView.getScene().getWindow() : null;
            if (owner != null) {
                popup.show(owner, x, y);
            } else {
                popup.show(webView.getScene().getWindow(), x, y);
            }
            activePopup = popup;
        });
    }

    public static void showAt(WebView webView, double anchorX, double anchorY, String itemsJson, String selectedId, Consumer<String> onSelect) {
        Platform.runLater(() -> {
            close();

            List<Map<String, String>> items = GSON.fromJson(itemsJson, ITEMS_TYPE);
            if (items == null || items.isEmpty()) return;

            ListView<String> list = new ListView<>();
            list.setItems(FXCollections.observableArrayList(
                    items.stream().map(m -> m.getOrDefault("label", m.getOrDefault("id", ""))).toList()));
            list.setPrefWidth(300);
            list.setPrefHeight(Math.min(260, Math.max(80, items.size() * 30 + 8)));
            list.setStyle(
                    "-fx-background-color: #232323;" +
                    "-fx-control-inner-background: #232323;" +
                    "-fx-text-fill: #e8e8e8;" +
                    "-fx-border-color: #ffd379;" +
                    "-fx-border-width: 1;");

            int selectedIndex = 0;
            for (int i = 0; i < items.size(); i++) {
                if (selectedId != null && selectedId.equals(items.get(i).get("id"))) {
                    selectedIndex = i;
                    break;
                }
            }
            list.getSelectionModel().select(selectedIndex);
            list.scrollTo(selectedIndex);

            Popup popup = new Popup();
            popup.setAutoHide(true);
            popup.setAutoFix(false);
            popup.getContent().add(list);

            list.setOnMouseClicked(event -> {
                int idx = list.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < items.size()) {
                    onSelect.accept(items.get(idx).get("id"));
                }
                popup.hide();
                activePopup = null;
            });

            Bounds viewBounds = webView.localToScreen(webView.getBoundsInLocal());
            double screenX = viewBounds.getMinX() + anchorX;
            double screenY = viewBounds.getMinY() + anchorY;
            double popupHeight = list.getPrefHeight();

            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            if (screenY + popupHeight > screen.getMaxY() - 8) {
                screenY = screenY - popupHeight - 36;
            }

            Window owner = webView.getScene().getWindow();
            popup.show(owner, screenX, screenY);
            activePopup = popup;
        });
    }

    public static void close() {
        if (activePopup != null) {
            activePopup.hide();
            activePopup = null;
        }
    }
}
