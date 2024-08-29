package p3;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;


public class ThemeEngine {

    Map<String, Color[]> availableThemes = new HashMap<>();
    Color[] theme;

    ThemeEngine() {
        loadThemes();
    }

    void setTheme() {
        theme = availableThemes.get("Fresh Blue");
    }

    private void loadThemes() {
        // theme is an array of ints (colors in hex) in order: darker, dark, neutral, light, lightest
        Color[] theme_01 = {Color.decode("#000000"), Color.decode("#5151cf"),
                Color.decode("#809fff"), Color.decode("#bfcfff"),
                Color.decode("#ffffff")};
        availableThemes.put("Fresh Blue", theme_01);
    }
}
