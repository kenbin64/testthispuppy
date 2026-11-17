package com.testthispuppy;

import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/** Scans the open page for every field a person could fill in. */
public class PageScanner {

    public List<FormField> scan(WebDriver driver) {
        List<FormField> fields = new ArrayList<>();
        for (WebElement element : driver.findElements(By.cssSelector("input, select, textarea"))) {
            String tag = element.getTagName().toLowerCase();
            String type = tag.equals("input") ? valueOr(element.getAttribute("type"), "text").toLowerCase() : tag;
            String name = element.getAttribute("name");
            boolean fillable = name != null && !name.isBlank()
                    && !List.of("hidden", "submit", "button", "reset", "image").contains(type);
            if (fillable) {
                List<String> options = new ArrayList<>();
                if (tag.equals("select")) {
                    for (WebElement option : element.findElements(By.tagName("option"))) {
                        String value = option.getAttribute("value");
                        if (value != null && !value.isBlank()) {
                            options.add(value);
                        }
                    }
                }
                fields.add(new FormField(tag, type, name, options));
            }
        }
        return fields;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
