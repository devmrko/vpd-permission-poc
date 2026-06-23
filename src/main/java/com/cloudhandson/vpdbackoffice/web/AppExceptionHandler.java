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
    String detail = exception.getMostSpecificCause() == null
        ? exception.getMessage()
        : exception.getMostSpecificCause().getMessage();
    model.addAttribute("errorTitle", "ADB 연결 설정이 필요합니다.");
    model.addAttribute("errorMessage",
        "BACKOFFICE_DB_URL, BACKOFFICE_DB_USERNAME, BACKOFFICE_DB_PASSWORD를 실제 ADB 값으로 설정하고 "
            + "./run.sh backoffice-support를 실행한 뒤 다시 시도하세요. 상세: " + detail);
    return "error";
  }
}
