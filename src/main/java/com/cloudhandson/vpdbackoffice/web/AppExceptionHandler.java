package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.AppException;
import org.springframework.dao.DataAccessException;
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

  @ExceptionHandler(DataAccessException.class)
  public String handleDataAccessException(DataAccessException exception, Model model) {
    RuntimeErrorMessage error = RuntimeErrorMessages.dataAccess(exception);
    model.addAttribute("errorTitle", error.title());
    model.addAttribute("errorMessage", error.message());
    return "error";
  }

  @ExceptionHandler(Exception.class)
  public String handleUnexpectedException(Exception exception, Model model) {
    RuntimeErrorMessage error = RuntimeErrorMessages.unexpected(exception);
    model.addAttribute("errorTitle", error.title());
    model.addAttribute("errorMessage", error.message());
    return "error";
  }
}
