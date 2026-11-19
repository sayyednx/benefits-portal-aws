package com.nabeelsayyed.benefitsportal.notification;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * The content of one email, before it's turned into HTML and plain text.
 *
 * @param subject    the subject line
 * @param heading    the big title at the top
 * @param intro      the first paragraph
 * @param highlights label/value pairs shown in a box, like "Confirmation number: UI-..."
 * @param details    more paragraphs
 * @param buttonText the call-to-action button text
 * @param buttonUrl  where the button goes
 */
public record Notice(String subject, String heading, String intro, LinkedHashMap<String, String> highlights,
                     List<String> details, String buttonText, String buttonUrl) {
}
