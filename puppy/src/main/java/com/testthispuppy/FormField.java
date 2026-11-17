package com.testthispuppy;

import java.util.List;

/**
 * One fillable thing found on a page.
 *
 * @param tag     input, select or textarea
 * @param type    for an input, its type attribute (text, email, number, date, checkbox...); otherwise the tag
 * @param name    the field's name attribute, which is what the form sends
 * @param options for a select, the values of its options that are not empty; otherwise empty
 */
public record FormField(String tag, String type, String name, List<String> options) {
}
