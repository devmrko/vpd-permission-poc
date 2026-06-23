package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.AppException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AppExceptionHandler {

  @ExceptionHandler(AppException.class)
  public String handleAppException(AppException exception, Model model) {
    model.addAttribute("errorMessage", exception.getMessage());
    return "error";
  }
}
