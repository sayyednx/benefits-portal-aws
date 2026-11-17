package com.nabeelsayyed.benefitsportal.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A file over the server's upload limit is rejected before it reaches the controller.
 * Instead of an error page, send the claimant back to their claim with a clear message.
 */
@ControllerAdvice(assignableTypes = ClaimantController.class)
public class UploadErrorHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooBig(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "That file is too big. Files can be up to 5 MB.");
        return "redirect:/status/claim";
    }
}
