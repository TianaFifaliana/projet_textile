package com.example.textile.controller.web;

import com.example.textile.exception.BusinessException;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URL;

@Slf4j
@ControllerAdvice(basePackages = "com.example.textile.controller.web")
public class GlobalWebExceptionHandler {

    @ExceptionHandler({InvalidOperationException.class, BusinessException.class})
    public String handleBusinessError(RuntimeException ex,
                                      HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + extractBaseUrl(request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + extractBaseUrl(request);
    }

    @ExceptionHandler(ConflictException.class)
    public String handleConflict(ConflictException ex,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + extractBaseUrl(request);
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        log.error("Erreur inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Une erreur inattendue est survenue : " + ex.getMessage());
        return "redirect:" + extractBaseUrl(request);
    }

    private String extractBaseUrl(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                String path = new URL(referer).getPath();
                String ctx = request.getContextPath();
                if (ctx != null && !ctx.isBlank() && path.startsWith(ctx)) {
                    path = path.substring(ctx.length());
                }
                if (path != null && path.matches("^/commandes(/.*)?$")) {
                    return path;
                }
            } catch (Exception ignored) {
            }
        }
        return "/commandes";
    }
}
